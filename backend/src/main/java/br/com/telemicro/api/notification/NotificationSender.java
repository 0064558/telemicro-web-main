package br.com.telemicro.api.notification;

import java.util.UUID;
import org.springframework.mail.SimpleMailMessage;

@FunctionalInterface
public interface NotificationSender {
    void send(UUID budgetId, SimpleMailMessage message);
}
