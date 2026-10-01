package br.com.telemicro.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private final AdminBootstrapService service;
    private final String adminEmail, adminPassword, demoEmail, demoPassword;
    private final boolean demoEnabled;

    public AdminBootstrap(AdminBootstrapService service,
            @Value("${app.bootstrap.admin-email:}") String adminEmail,
            @Value("${app.bootstrap.admin-password:}") String adminPassword,
            @Value("${app.bootstrap.demo-email:}") String demoEmail,
            @Value("${app.bootstrap.demo-password:}") String demoPassword,
            @Value("${app.demo-enabled:false}") boolean demoEnabled) {
        this.service = service;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.demoEmail = demoEmail;
        this.demoPassword = demoPassword;
        this.demoEnabled = demoEnabled;
    }
    @Override public void run(ApplicationArguments args) {
        service.bootstrap(adminEmail, adminPassword, demoEmail, demoPassword, demoEnabled);
    }
}
