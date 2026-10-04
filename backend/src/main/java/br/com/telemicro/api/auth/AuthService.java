package br.com.telemicro.api.auth;

import br.com.telemicro.api.config.SecurityProperties;
import br.com.telemicro.api.shared.ApiException;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class AuthService {
    private final AdminUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;
    private final boolean demoEnabled;
    private final String dummyHash;

    public AuthService(AdminUserRepository users, PasswordEncoder passwords, JwtEncoder encoder,
            SecurityProperties properties, Clock clock, @Value("${app.demo-enabled:false}") boolean demoEnabled) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.demoEnabled = demoEnabled;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A senha deve ter até 72 bytes UTF-8.", "password");
        }
        var user = users.findByEmail(request.email().strip().toLowerCase(Locale.ROOT));
        boolean matched = passwords.matches(request.password(), user.map(AdminUser::passwordHash).orElse(dummyHash));
        if (!matched || user.isEmpty() || !user.get().active() || (user.get().role().equals("DEMO") && !demoEnabled)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        }
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(user.get().id().toString()).issuedAt(now).expiresAt(now.plusSeconds(properties.tokenTtlSeconds()))
                .id(UUID.randomUUID().toString()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", properties.tokenTtlSeconds(), profile(user.get()));
    }

    // Esse record representa a requisição de alteração de senha, contendo a senha atual e a nova senha.
    public record ChangePasswordRequest(
            @NotBlank(message = "Informe a senha atual.")
            @Size(max = 72)
            String currentPassword,

            @NotBlank(message = "Informe a nova senha.")
            @Size(min = 12, max = 72, message = "A nova senha deve ter entre 12 e 72 caracteres.")
            String newPassword
    ) {}

    public UserResponse me(UUID id) {
        return profile(users.findById(id).filter(AdminUser::active)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida.")));
    }

    // O método changePassword permite que um usuário altere sua senha.
    //  Ele verifica se a senha atual fornecida corresponde à senha armazenada,
    //  se a nova senha é diferente da atual e se ambas as senhas estão dentro do limite de bytes UTF-8.
    //  Se todas as condições forem atendidas, ele atualiza a senha no banco de dados.
    public void changePassword(UUID id, ChangePasswordRequest request) {
        // Verifica se a senha atual e a nova senha não excedem 72 bytes em UTF-8.
        if (request.currentPassword()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || request.newPassword()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "A senha deve ter até 72 bytes UTF-8."
            );
        }

        // Busca o usuário pelo ID e verifica se ele está ativo. Se não estiver, lança uma exceção de sessão inválida.
        var user = users.findById(id)
                .filter(AdminUser::active)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "Sessão inválida."
                ));

        // Verifica se o usuário tem permissão para alterar a senha.
        if (!user.role().equals("ADMIN")) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Você não tem permissão para alterar a senha."
            );
        }

        // Verifica se a senha atual fornecida corresponde à senha armazenada. Se não corresponder, lança uma exceção informando que a senha atual está incorreta.
        if (!passwords.matches(request.currentPassword(), user.passwordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "A senha atual está incorreta.",
                    "currentPassword"
            );
        }

        // Verifica se a nova senha é diferente da senha atual. Se for igual, lança uma exceção informando que a nova senha deve ser diferente da senha atual.
        if (passwords.matches(request.newPassword(), user.passwordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "A nova senha deve ser diferente da senha atual.",
                    "newPassword"
            );
        }

        // Codifica a nova senha e atualiza o hash da senha no banco de dados. Se a atualização falhar, lança uma exceção informando que não foi possível alterar a senha.
        String newHash = passwords.encode(request.newPassword());

        // Atualiza a senha do usuário no banco de dados, se o hash armazenada ainda for o mesmo.
        boolean updated = users.updatePassword(id, user.passwordHash(), newHash);

        // Se a atualização falhar, lança uma exceção informando que não foi possível alterar a senha.
        if (!updated) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Não foi possível alterar a senha. Tente novamente."
            );
        }
    }

    private UserResponse profile(AdminUser user) { return new UserResponse(user.id(), user.email(), user.role()); }

    public record LoginRequest(@NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.")
                               @Size(max = 254) String email,
                               @NotBlank(message = "Informe a senha.") @Size(max = 72) String password) {}
    public record UserResponse(UUID id, String email, String role) {}
    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}
}
