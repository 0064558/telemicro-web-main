package br.com.telemicro.api.budget;

import br.com.telemicro.api.servicecatalog.ServiceTypeRepository;
import br.com.telemicro.api.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class BudgetService {
    private final ServiceTypeRepository services;
    private final BudgetWriteRepository budgets;

    public BudgetService(ServiceTypeRepository services, BudgetWriteRepository budgets) {
        this.services = services;
        this.budgets = budgets;
    }

    @Transactional(timeout = 15)
    public BudgetCreatedResponse create(String key, CreateBudgetRequest request) {
        if (!key.matches("[A-Za-z0-9_-]{16,100}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use uma chave de 16 a 100 caracteres alfanuméricos, hífen ou sublinhado.", "idempotencyKey");
        }
        String name = request.customerName().strip();
        String phone = request.phone().replaceAll("\\D", "");
        if (!phone.matches("(?:55)?[1-9][0-9][2-9][0-9]{7,8}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe um telefone válido com DDD.", "phone");
        }
        if (!phone.startsWith("55") || phone.length() <= 11) phone = "55" + phone;
        String code = request.serviceCode().strip();
        String message = request.message() == null ? "" : request.message().strip();
        if (name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Informe seu nome.", "customerName");
        String hash = hash(name, phone, code, message);

        budgets.lockKey(key);
        // O instante é calculado após adquirir o lock: uma requisição concorrente pode ter aguardado.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var previous = budgets.findPrevious(key, now);
        if (previous.isPresent()) {
            if (!previous.get().payloadHash().equals(hash)) {
                throw new ApiException(HttpStatus.CONFLICT, "Esta chave já foi usada para outro pedido.");
            }
            return previous.get().response();
        }
        var service = services.findByCodeAndActiveTrue(code).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "Selecione um serviço ativo do catálogo.", "serviceCode"));

        for (int attempt = 0; attempt < 5; attempt++) {
            UUID id = UUID.randomUUID();
            String protocol = "TM-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
            if (budgets.insertBudget(id, protocol, name, phone, service.getId(), message, now)) {
                budgets.insertInitialHistory(id, now);
                budgets.remember(key, hash, id, now.plus(24, ChronoUnit.HOURS));
                return new BudgetCreatedResponse(protocol, now);
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível gerar o protocolo. Tente novamente.");
    }

    private static String hash(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Prefixos de tamanho evitam ambiguidades entre os campos, mesmo com quebras de linha.
            for (String field : fields) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
