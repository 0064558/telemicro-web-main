package br.com.telemicro.api.auth;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthService.LoginResponse> login(@Valid @RequestBody AuthService.LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthService.UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.me(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AuthService.ChangePasswordRequest request
    ) {
        // O ID do usuário é obtido a partir do token JWT, que é passado como parâmetro para o método.
        UUID userId = UUID.fromString(jwt.getSubject());

        // Chama o serviço de autenticação para alterar a senha do usuário com o ID fornecido, usando a requisição de alteração de senha.
        auth.changePassword(userId, request);

        // Retorna uma resposta HTTP 204 (No Content) com o cabeçalho de controle de cache configurado para não armazenar em cache.
        return ResponseEntity
                .noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
