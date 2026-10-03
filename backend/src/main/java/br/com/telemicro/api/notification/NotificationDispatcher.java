package br.com.telemicro.api.notification;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class NotificationDispatcher {
    // O log não deve registrar credenciais nem conteúdo de mensagens de erro do provedor.
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    // O JdbcTemplate é usado para buscar e atualizar avisos pendentes na tabela budget_notifications.
    private final JdbcTemplate jdbc;
    // O JavaMailSender é usado para enviar e-mails de aviso.
    private final JavaMailSender mail;

    // O remetente, destinatário e URL do painel são configurados via application.properties.
    private final String from;
    private final String to;
    private final String panelUrl;

    // O construtor lança exceção se não houver remetente, destinatário e URL do painel configurados.
    public NotificationDispatcher(JdbcTemplate jdbc, JavaMailSender mail,
            @Value("${app.notifications.from}") String from,
            @Value("${app.notifications.to}") String to,
            @Value("${app.notifications.panel-url}") String panelUrl) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.from = from;
        this.to = to;
        this.panelUrl = panelUrl;
        if (from.isBlank() || to.isBlank() || panelUrl.isBlank()) {
            throw new IllegalArgumentException("Configure remetente, destinatário e URL do painel para ativar notificações.");
        }
    }

    // O método dispatch() é executado periodicamente para enviar avisos de orçamento pendentes.
    @Scheduled(fixedDelayString = "${app.notifications.interval-ms:15000}")
    @Transactional
    public void dispatch() {
        // O lock impede dois processos de enviarem o mesmo aviso simultaneamente.
        var pending = jdbc.query("""
                SELECT budget_id, protocol, service_name FROM budget_notifications
                WHERE sent_at IS NULL AND next_attempt_at <= now()
                ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new Pending(rs.getObject("budget_id", UUID.class),
                rs.getString("protocol"), rs.getString("service_name")));
        if (pending.isEmpty()) return;
        Pending item = pending.getFirst();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Telemicro — Novo orçamento " + item.protocol());
        message.setText("Novo orçamento recebido.\n\nProtocolo: " + item.protocol()
                + "\nServiço: " + item.serviceName() + "\n\nConsulte e atenda pelo painel: " + panelUrl
                + "\n\nTelemicro — Notificações");
        try {
            mail.send(message);
        } catch (RuntimeException exception) {
            // Se falhar, agendar nova tentativa em 5 minutos e registrar aviso no log.
            jdbc.update("""
                    UPDATE budget_notifications SET attempts = attempts + 1,
                    next_attempt_at = now() + interval '5 minutes' WHERE budget_id = ?
                    """, item.id());
            // Não registrar credenciais nem conteúdo de mensagens de erro do provedor.
            log.warn("Falha ao enviar aviso {}. Nova tentativa em 5 minutos ({})", item.protocol(), exception.getClass().getSimpleName());
            return;
        }
        // Se enviar com sucesso, registrar a data de envio e incrementar o contador de tentativas.
        jdbc.update("UPDATE budget_notifications SET sent_at = now(), attempts = attempts + 1 WHERE budget_id = ?", item.id());
    }

    // A classe Pending é um registro que representa um aviso de orçamento pendente, com id, protocolo e nome do serviço.
    private record Pending(UUID id, String protocol, String serviceName) {}
}
