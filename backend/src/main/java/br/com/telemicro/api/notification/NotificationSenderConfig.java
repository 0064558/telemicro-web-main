package br.com.telemicro.api.notification;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class NotificationSenderConfig {
    @Bean
    @ConditionalOnProperty(name = "app.notifications.provider", havingValue = "smtp", matchIfMissing = true)
    NotificationSender smtpNotificationSender(JavaMailSender mail) {
        return (budgetId, message) -> mail.send(message);
    }

    @Bean
    @ConditionalOnProperty(name = "app.notifications.provider", havingValue = "resend")
    NotificationSender resendNotificationSender(@Value("${app.notifications.resend-api-key:}") String apiKey) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        var client = RestClient.builder().baseUrl("https://api.resend.com").requestFactory(factory).build();
        return new ResendNotificationSender(client, apiKey);
    }
}
