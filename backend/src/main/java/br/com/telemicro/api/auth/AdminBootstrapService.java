package br.com.telemicro.api.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AdminBootstrapService {
    private final AdminUserRepository users;
    private final PasswordEncoder passwords;
    public AdminBootstrapService(AdminUserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public void bootstrap(String adminEmail, String adminPassword, String demoEmail, String demoPassword, boolean demoEnabled) {
        String email = normalizeEmail(adminEmail);
        validatePassword(adminPassword);
        boolean createDemo = !demoEmail.isBlank() || !demoPassword.isBlank();
        String normalizedDemoEmail = createDemo ? normalizeEmail(demoEmail) : "";
        if (createDemo) {
            if (!demoEnabled) throw new IllegalStateException("Bootstrap DEMO exige DEMO_ENABLED=true.");
            validatePassword(demoPassword);
            if (email.equals(normalizedDemoEmail)) throw new IllegalStateException("E-mails ADMIN e DEMO devem ser diferentes.");
        }
        users.lockBootstrap();
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (!existing.get().role().equals("ADMIN")) throw new IllegalStateException("E-mail já pertence a outro papel.");
            // Reiniciar nunca redefine senhas nem reativa contas existentes.
        } else {
            if (users.hasAdmin()) throw new IllegalStateException("Administrador já existe; bootstrap não cadastra administradores adicionais.");
            users.create(email, passwords.encode(adminPassword), "ADMIN");
        }
        if (createDemo) {
            var demo = users.findByEmail(normalizedDemoEmail);
            if (demo.isPresent() && !demo.get().role().equals("DEMO")) throw new IllegalStateException("E-mail DEMO já pertence a outro papel.");
            if (demo.isEmpty()) users.create(normalizedDemoEmail, passwords.encode(demoPassword), "DEMO");
        }
    }

    private static String normalizeEmail(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalStateException("Configure um e-mail válido para o bootstrap.");
        }
        return normalized;
    }
    private static void validatePassword(String password) {
        if (password.isBlank() || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Senha de bootstrap exige pelo menos 12 caracteres e no máximo 72 bytes UTF-8.");
        }
    }
}
