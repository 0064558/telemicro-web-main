package br.com.telemicro.api;

import br.com.telemicro.api.budget.BudgetService;
import br.com.telemicro.api.budget.CreateBudgetRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PublicBudgetApiTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.9-bookworm");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BudgetService service;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;

    private static final String PAYLOAD = """
            {"customerName":"  Cliente Teste  ","phone":"(33) 99999-9999",
             "serviceCode":"TECHNICAL_ASSISTANCE","message":"  Notebook não liga.  "}
            """;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM budget_idempotency");
        jdbc.update("DELETE FROM budget_notes");
        jdbc.update("DELETE FROM budget_status_history");
        jdbc.update("DELETE FROM budget_requests");
        jdbc.update("DELETE FROM admin_users");
        jdbc.update("UPDATE service_types SET active = true");
    }

    @Test
    void demoCredentialsAndSignedDemoTokenAreRejectedWhenDemoModeIsDisabled() throws Exception {
        UUID id = UUID.randomUUID();
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("Senha-de-teste-123");
        jdbc.update("INSERT INTO admin_users(id, email, password_hash, role) VALUES (?, 'demo@example.test', ?, 'DEMO')", id, hash);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"demo@example.test\",\"password\":\"Senha-de-teste-123\"}"))
                .andExpect(status().isUnauthorized());
        var now = java.time.Instant.now();
        String token = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("telemicro-api").subject(id.toString())
                        .audience(java.util.List.of("telemicro-admin")).issuedAt(now).expiresAt(now.plusSeconds(60)).build())).getTokenValue();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void listsOnlyActiveServicesInDisplayOrder() throws Exception {
        jdbc.update("UPDATE service_types SET active = false WHERE code = 'OTHER'");
        mvc.perform(get("/api/v1/services")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].code").value("TECHNICAL_ASSISTANCE"))
                .andExpect(jsonPath("$[0].name").value("Assistência técnica"));
    }

    @Test
    void createsNormalizedBudgetAndInitialHistoryWithoutExposingPersonalData() throws Exception {
        mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocol").value(org.hamcrest.Matchers.matchesPattern("TM-[A-F0-9]{32}")))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT customer_name FROM budget_requests", String.class)).isEqualTo("Cliente Teste");
        assertThat(jdbc.queryForObject("SELECT phone FROM budget_requests", String.class)).isEqualTo("5533999999999");
        assertThat(jdbc.queryForObject("SELECT message FROM budget_requests", String.class)).isEqualTo("Notebook não liga.");
        assertThat(jdbc.queryForObject("SELECT status FROM budget_requests", String.class)).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_status_history WHERE new_status = 'NEW' AND previous_status IS NULL AND changed_by IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void replayReturnsExactOriginalResponseEvenAfterServiceBecomesInactive() throws Exception {
        String key = UUID.randomUUID().toString();
        String original = mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andReturn().getResponse().getContentAsString();
        jdbc.update("UPDATE service_types SET active = false WHERE code = 'TECHNICAL_ASSISTANCE'");
        mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD.replace("(33) 99999-9999", "+55 (33) 99999-9999")))
                .andExpect(status().isCreated()).andExpect(content().json(original));
        assertCounts(1);
    }

    @Test
    void sameKeyWithChangedContentReturnsConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD.replace("Notebook não liga.", "Outro pedido.")))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertCounts(1);
    }

    @Test
    void rejectsInvalidFieldsMissingKeyAndMalformedJsonWithoutWriting() throws Exception {
        mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerName":" ","phone":"abc","serviceCode":"","message":""}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.customerName").exists())
                .andExpect(jsonPath("$.errors.phone").exists()).andExpect(jsonPath("$.errors.serviceCode").exists());
        mvc.perform(post("/api/v1/budgets").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", "short")
                .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.idempotencyKey").exists());
        mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        assertCounts(0);
    }

    @Test
    void rejectsInvalidPhoneAndUnavailableService() throws Exception {
        mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD.replace("(33) 99999-9999", "123")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.phone").exists());
        for (String code : new String[]{"UNKNOWN", "TECHNICAL_ASSISTANCE"}) {
            jdbc.update("UPDATE service_types SET active = false WHERE code = 'TECHNICAL_ASSISTANCE'");
            mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD.replace("TECHNICAL_ASSISTANCE", code)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.serviceCode").exists());
        }
        assertCounts(0);
    }

    @Test
    void expiredKeyCanBeUsedForNewRequest() throws Exception {
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isCreated());
        jdbc.update("UPDATE budget_idempotency SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'");
        mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_requests", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_idempotency", Integer.class)).isEqualTo(1);
    }

    @Test
    void simultaneousRequestsWithSameKeyCreateOnlyOneBudget() throws Exception {
        String key = UUID.randomUUID().toString();
        CreateBudgetRequest request = new CreateBudgetRequest("Teste", "33999999999", "OTHER", null);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return service.create(key, request); });
            var second = executor.submit(() -> { start.await(); return service.create(key, request); });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        }
        assertCounts(1);
    }

    @Test
    void failureWritingHistoryRollsBackBudgetAndKey() throws Exception {
        jdbc.execute("ALTER TABLE budget_status_history ADD CONSTRAINT reject_initial_for_test CHECK (new_status <> 'NEW')");
        try {
            mvc.perform(post("/api/v1/budgets").header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("Não foi possível concluir a solicitação. Tente novamente."));
            assertCounts(0);
        } finally {
            jdbc.execute("ALTER TABLE budget_status_history DROP CONSTRAINT reject_initial_for_test");
        }
    }

    private void assertCounts(int count) {
        for (String table : new String[]{"budget_requests", "budget_status_history", "budget_idempotency"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isEqualTo(count);
        }
    }
}
