package br.com.telemicro.api.notification;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class ResendNotificationSender implements NotificationSender {
    private final RestClient client;
    private final String apiKey;

    public ResendNotificationSender(RestClient client, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Configure RESEND_API_KEY para ativar o envio pelo Resend.");
        }
        this.client = client;
        this.apiKey = apiKey;
    }

    @Override
    public void send(UUID budgetId, SimpleMailMessage message) {
        try {
            client.post().uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    // O provedor reutiliza a resposta por 24 horas para esta mesma notificacao.
                    .header("Idempotency-Key", "budget/" + budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", message.getFrom(), "to", List.of(message.getTo()),
                            "subject", message.getSubject(), "text", message.getText()))
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw new IllegalStateException("O provedor nao confirmou o envio da notificacao.");
                    })
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            // A resposta do provedor pode conter enderecos e outros dados privados.
            throw new IllegalStateException("O provedor nao confirmou o envio da notificacao.");
        }
    }
}
