package br.com.telemicro.api;

import br.com.telemicro.api.budget.*;
import br.com.telemicro.api.notification.*;
import br.com.telemicro.api.servicecatalog.ServiceTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class BudgetNotificationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.9-bookworm");
    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceTypeRepository services;
    @Autowired BudgetWriteRepository budgets;
    @Autowired PlatformTransactionManager transactions;
    @Autowired BudgetService defaultService;

    private BudgetService enabledService() {
        return new BudgetService(services, budgets, new BudgetNotifications(jdbc, true));
    }

    private BudgetCreatedResponse create(String key) {
        return new TransactionTemplate(transactions).execute(status -> enabledService().create(key,
                new CreateBudgetRequest("Teste", "33999999999", "TECHNICAL_ASSISTANCE", "Mensagem privada")));
    }

    @org.junit.jupiter.api.BeforeEach
    void clear() {
        jdbc.update("DELETE FROM budget_idempotency");
        jdbc.update("DELETE FROM budget_notes");
        jdbc.update("DELETE FROM budget_status_history");
        jdbc.update("DELETE FROM budget_requests");
    }

    @Test
    void disabledNotificationsDoNotQueueMessages() {
        defaultService.create(UUID.randomUUID().toString(),
                new CreateBudgetRequest("Teste", "33999999999", "TECHNICAL_ASSISTANCE", ""));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_notifications", Integer.class)).isZero();
    }

    @Test
    void replayDoesNotDuplicateNotificationAndRollbackDoesNotQueueIt() {
        String key = UUID.randomUUID().toString();
        create(key);
        create(key);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_notifications", Integer.class)).isEqualTo(1);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            enabledService().create(UUID.randomUUID().toString(),
                    new CreateBudgetRequest("Rollback", "33999999999", "TECHNICAL_ASSISTANCE", ""));
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_notifications", Integer.class)).isEqualTo(1);
    }

    @Test
    void failurePreservesBudgetAndRetriesThenMarksSentWithoutExposingCustomerData() {
        var response = create(UUID.randomUUID().toString());
        JavaMailSender mail = mock(JavaMailSender.class);
        doThrow(new MailSendException("Erro simulado")).doNothing().when(mail).send(any(SimpleMailMessage.class));
        var dispatcher = new NotificationDispatcher(jdbc, mail, "sender@example.test", "recipient@example.test", "http://localhost:4200/admin");
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> dispatcher.dispatch());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_requests", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempts FROM budget_notifications", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sent_at IS NULL AND next_attempt_at > now() FROM budget_notifications", Boolean.class)).isTrue();
        transaction.executeWithoutResult(status -> dispatcher.dispatch());
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        jdbc.update("UPDATE budget_notifications SET next_attempt_at = now()");
        transaction.executeWithoutResult(status -> dispatcher.dispatch());
        transaction.executeWithoutResult(status -> dispatcher.dispatch());
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(2)).send(message.capture());
        assertThat(message.getValue().getText()).contains(response.protocol(), "Assistência técnica", "/admin")
                .doesNotContain("33999999999", "Mensagem privada");
        assertThat(jdbc.queryForObject("SELECT sent_at IS NOT NULL FROM budget_notifications", Boolean.class)).isTrue();
    }
}
