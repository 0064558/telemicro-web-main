package br.com.telemicro.api.notification;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.mockito.Mockito.*;

class ResendNotificationSenderTests {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final UUID budgetId = UUID.fromString("a7603c20-1d66-42d2-bda7-a1750745a226");

    @Test
    void sendsJsonWithAuthorizationAndStableIdempotencyKeyForRetries() {
        for (int attempt = 0; attempt < 2; attempt++) {
            server.expect(requestTo("https://api.resend.com/emails"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer test-api-key"))
                    .andExpect(header("Idempotency-Key", "budget/" + budgetId))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(content().json("""
                        {"from":"sender@example.test","to":["recipient@example.test"],
                         "subject":"Telemicro — Novo orçamento TEST-123",
                         "text":"Protocolo: TEST-123\\nServiço: Assistência técnica"}
                        """))
                    .andRespond(withSuccess("{\"id\":\"email-id\"}", MediaType.APPLICATION_JSON));
        }
        var sender = new ResendNotificationSender(builder.build(), "test-api-key");
        sender.send(budgetId, message());
        sender.send(budgetId, message());
        server.verify();
    }

    @Test
    void providerFailureDoesNotExposeItsResponseAndCanBeRetriedByDispatcher() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("recipient@example.test private-provider-detail"));
        var sender = new ResendNotificationSender(builder.build(), "test-api-key");
        assertThatThrownBy(() -> sender.send(budgetId, message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("O provedor nao confirmou o envio da notificacao.")
                .hasNoCause();
        server.verify();
    }

    @Test
    void missingApiKeyFailsBeforeAnyRequest() {
        assertThatThrownBy(() -> new ResendNotificationSender(builder.build(), " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RESEND_API_KEY");
    }

    @Test
    void redirectIsNotTreatedAsAnAcceptedEmail() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.FOUND));
        var sender = new ResendNotificationSender(builder.build(), "test-api-key");
        assertThatThrownBy(() -> sender.send(budgetId, message())).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void resendConfigurationStartsWithoutSmtpCredentialsOrSender() {
        new ApplicationContextRunner().withUserConfiguration(NotificationSenderConfig.class)
                .withPropertyValues("app.notifications.enabled=true", "app.notifications.provider=resend",
                        "app.notifications.resend-api-key=test-api-key")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(NotificationSender.class);
                    assertThat(context.getBean(NotificationSender.class)).isInstanceOf(ResendNotificationSender.class);
                });
    }

    @Test
    void smtpRemainsTheDefaultForLocalUse() {
        var mail = mock(JavaMailSender.class);
        new ApplicationContextRunner().withUserConfiguration(NotificationSenderConfig.class)
                .withBean(JavaMailSender.class, () -> mail)
                .withPropertyValues("app.notifications.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(NotificationSender.class);
                    var message = message();
                    context.getBean(NotificationSender.class).send(budgetId, message);
                    verify(mail).send(message);
                });
    }

    private SimpleMailMessage message() {
        var message = new SimpleMailMessage();
        message.setFrom("sender@example.test");
        message.setTo("recipient@example.test");
        message.setSubject("Telemicro — Novo orçamento TEST-123");
        message.setText("Protocolo: TEST-123\nServiço: Assistência técnica");
        return message;
    }
}
