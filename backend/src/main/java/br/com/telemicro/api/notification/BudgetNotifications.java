package br.com.telemicro.api.notification;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BudgetNotifications {
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public BudgetNotifications(JdbcTemplate jdbc, @Value("${app.notifications.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    // Participa da mesma transação do pedido: rollback também remove o aviso.
    public void enqueue(UUID budgetId, String protocol, String serviceName) {
        if (enabled) jdbc.update("INSERT INTO budget_notifications(budget_id, protocol, service_name) VALUES (?, ?, ?)",
                budgetId, protocol, serviceName);
    }
}
