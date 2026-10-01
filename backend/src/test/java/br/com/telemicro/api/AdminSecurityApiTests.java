package br.com.telemicro.api;

import br.com.telemicro.api.auth.AdminBootstrapService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.demo-enabled=true", "app.security.login-limit=3", "app.security.submission-limit=3"})
@AutoConfigureMockMvc
@Testcontainers
class AdminSecurityApiTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.9-bookworm");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired AdminBootstrapService bootstrap;

    private static final UUID ADMIN = UUID.randomUUID(), DEMO = UUID.randomUUID();
    private static final UUID REAL_BUDGET = UUID.randomUUID(), DEMO_BUDGET = UUID.randomUUID();
    private static final String PASSWORD = "Teste-seguro-123!";
    private static final String HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
    private static final AtomicInteger ADDRESSES = new AtomicInteger();
    private String ip;

    @BeforeEach
    void fixtures() {
        ip = "192.0.2." + ADDRESSES.incrementAndGet();
        jdbc.update("DELETE FROM budget_idempotency");
        jdbc.update("DELETE FROM budget_notes");
        jdbc.update("DELETE FROM budget_status_history");
        jdbc.update("DELETE FROM budget_requests");
        jdbc.update("DELETE FROM admin_users");
        jdbc.update("INSERT INTO admin_users(id, email, password_hash, role) VALUES (?, 'admin@example.test', ?, 'ADMIN'), (?, 'demo@example.test', ?, 'DEMO')", ADMIN, HASH, DEMO, HASH);
        for (UUID id : List.of(REAL_BUDGET, DEMO_BUDGET)) {
            jdbc.update("""
                    INSERT INTO budget_requests(id, protocol, customer_name, phone, service_type_id, is_demo, created_at)
                    SELECT ?, ?, ?, '5533999999999', id, ?, ? FROM service_types WHERE code = 'OTHER'
                    """, id, id.equals(REAL_BUDGET) ? "TM-REAL" : "TM-DEMO",
                    id.equals(REAL_BUDGET) ? "Cliente privado" : "Cliente fictício", id.equals(DEMO_BUDGET),
                    java.sql.Timestamp.from(Instant.parse(id.equals(REAL_BUDGET) ? "2026-01-01T12:00:00Z" : "2026-01-02T12:00:00Z")));
            jdbc.update("INSERT INTO budget_status_history(budget_request_id, new_status) VALUES (?, 'NEW')", id);
        }
    }

    @Test
    void publicSubmissionsAreNotAutomaticallyExposedToDemoUsers() throws Exception {
        mvc.perform(atIp(post("/api/v1/budgets")).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerName\":\"Novo pedido privado\",\"phone\":\"33999999999\",\"serviceCode\":\"OTHER\"}"))
                .andExpect(status().isCreated());
        String token = login("demo@example.test");
        mvc.perform(get("/api/v1/admin/budgets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(jdbc.queryForObject("SELECT is_demo FROM budget_requests WHERE customer_name = 'Novo pedido privado'", Boolean.class)).isFalse();
    }

    @Test
    void loginIssuesJwtAndMeReturnsCurrentUserWithoutPassword() throws Exception {
        String token = login("ADMIN@example.test");
        assertThat(token.split("\\.")).hasSize(3);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(ADMIN.toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordUnknownAndInactiveUsersHaveSameError() throws Exception {
        for (String email : List.of("admin@example.test", "unknown@example.test", "demo@example.test")) {
            jdbc.update("UPDATE admin_users SET active = false WHERE role = 'DEMO'");
            String password = email.startsWith("admin") ? "senha-errada" : PASSWORD;
            mvc.perform(atIp(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
        }
    }

    @Test
    void missingExpiredWrongIssuerAudienceAndForgedTokensAreRejected() throws Exception {
        mvc.perform(get("/api/v1/admin/budgets")).andExpect(status().isUnauthorized());
        List<String> tokens = List.of("invalid", token(encoder, "telemicro-api", "telemicro-admin", Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1)),
                token(encoder, "other", "telemicro-admin", Instant.now(), Instant.now().plusSeconds(60)),
                token(encoder, "telemicro-api", "other", Instant.now(), Instant.now().plusSeconds(60)),
                token(NimbusJwtEncoder.withSecretKey(new SecretKeySpec(new byte[32], "HmacSHA256")).build(), "telemicro-api", "telemicro-admin", Instant.now(), Instant.now().plusSeconds(60)));
        for (String token : tokens) {
            mvc.perform(get("/api/v1/admin/budgets").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void disablingUserInvalidatesAccessAndRoleChangeRemovesWritePermission() throws Exception {
        String token = login("admin@example.test");
        jdbc.update("UPDATE admin_users SET role = 'DEMO' WHERE id = ?", ADMIN);
        mvc.perform(patch("/api/v1/admin/budgets/" + DEMO_BUDGET + "/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\",\"version\":0}"))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE admin_users SET active = false WHERE id = ?", ADMIN);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void demoOnlySeesDemoBudgetsAndCannotWrite() throws Exception {
        String token = login("demo@example.test");
        mvc.perform(get("/api/v1/admin/budgets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(DEMO_BUDGET.toString()));
        mvc.perform(get("/api/v1/admin/budgets/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.services[0].code").value("OTHER"))
                .andExpect(jsonPath("$.services[0].total").value(1));
        mvc.perform(get("/api/v1/admin/budgets/" + REAL_BUDGET).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/budgets/" + DEMO_BUDGET).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/budgets/" + DEMO_BUDGET + "/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\",\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/budgets/" + DEMO_BUDGET + "/notes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Não deve salvar\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_notes", Integer.class)).isZero();
    }

    @Test
    void filtersPaginationAndSummaryAgreeAndSqlWildcardsAreLiteral() throws Exception {
        String token = login("admin@example.test");
        mvc.perform(get("/api/v1/admin/budgets").param("size", "1").param("page", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].id").value(REAL_BUDGET.toString()));
        mvc.perform(get("/api/v1/admin/budgets/summary").param("size", "1").param("page", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.services[0].total").value(2));
        for (String path : List.of("/api/v1/admin/budgets", "/api/v1/admin/budgets/summary")) {
            var result = mvc.perform(get(path).param("q", "FICTÍCIO").param("status", "NEW").param("serviceCode", "OTHER")
                    .param("from", "2026-01-02T00:00:00Z").param("to", "2026-01-03T00:00:00Z")
                    .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            result.andExpect(jsonPath(path.endsWith("summary") ? "$.total" : "$.totalElements").value(1));
        }
        mvc.perform(get("/api/v1/admin/budgets").param("q", "%").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/admin/budgets").param("sort", "OLDEST").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(REAL_BUDGET.toString()));
    }

    @Test
    void invalidFiltersAndUnknownBudgetReturnAppropriateErrors() throws Exception {
        String token = login("admin@example.test");
        for (Map<String, String> params : List.of(Map.of("size", "101"), Map.of("page", "-1"), Map.of("status", "WRONG"),
                Map.of("sort", "created_at;DELETE"), Map.of("from", "2026-01-03T00:00:00Z", "to", "2026-01-01T00:00:00Z"))) {
            var request = get("/api/v1/admin/budgets").header("Authorization", "Bearer " + token);
            params.forEach(request::param);
            mvc.perform(request).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/admin/budgets/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusVersionHistoryAndRequiredReopeningReasonAreEnforced() throws Exception {
        String token = login("admin@example.test");
        change(token, "COMPLETED", 0, null).andExpect(status().isConflict());
        change(token, "IN_PROGRESS", 0, null).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        change(token, "CANCELLED", 0, null).andExpect(status().isConflict());
        change(token, "COMPLETED", 1, null).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        change(token, "IN_PROGRESS", 2, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.justification").exists());
        change(token, "IN_PROGRESS", 2, "Cliente pediu nova avaliação").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        change(token, "IN_PROGRESS", 3, null).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        mvc.perform(get("/api/v1/admin/budgets/" + REAL_BUDGET).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(4))
                .andExpect(jsonPath("$.history[1].changedBy").value(ADMIN.toString()))
                .andExpect(jsonPath("$.notes[0].text").value("Cliente pediu nova avaliação"));
    }

    @Test
    void notesAreValidatedAndAttributed() throws Exception {
        String token = login("admin@example.test");
        mvc.perform(post("/api/v1/admin/budgets/" + REAL_BUDGET + "/notes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  Verificar fonte.  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("Verificar fonte."))
                .andExpect(jsonPath("$.authorId").value(ADMIN.toString()));
        mvc.perform(post("/api/v1/admin/budgets/" + REAL_BUDGET + "/notes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentStatusUpdatesHaveOneWinnerAndOneConflict() throws Exception {
        String token = login("admin@example.test");
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return change(token, "IN_PROGRESS", 0, null).andReturn().getResponse().getStatus(); });
            var second = executor.submit(() -> { start.await(); return change(token, "CANCELLED", 0, null).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_status_history WHERE budget_request_id = ?", Integer.class, REAL_BUDGET)).isEqualTo(2);
    }

    @Test
    void failedHistoryWriteRollsBackStatusVersionAndJustification() throws Exception {
        String token = login("admin@example.test");
        jdbc.execute("ALTER TABLE budget_status_history ADD CONSTRAINT reject_change_for_test CHECK (new_status <> 'IN_PROGRESS')");
        try {
            change(token, "IN_PROGRESS", 0, "Observação que deve ser revertida").andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT status FROM budget_requests WHERE id = ?", String.class, REAL_BUDGET)).isEqualTo("NEW");
            assertThat(jdbc.queryForObject("SELECT version FROM budget_requests WHERE id = ?", Long.class, REAL_BUDGET)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_notes", Integer.class)).isZero();
        } finally { jdbc.execute("ALTER TABLE budget_status_history DROP CONSTRAINT reject_change_for_test"); }
    }

    @Test
    void loginAndSubmissionsAreRateLimitedSeparatelyAndForwardedHeadersDoNotBypassLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(atIp(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"admin@example.test\",\"password\":\"errada\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(atIp(post("/api/v1/auth/login")).header("X-Forwarded-For", "203.0.113.10")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        for (int i = 0; i < 3; i++) {
            mvc.perform(atIp(post("/api/v1/budgets")).header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(atIp(post("/api/v1/budgets")).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/v1/services")).andExpect(status().isOk());
    }

    @Test
    void corsOnlyPermitsConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/v1/admin/budgets").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(options("/api/v1/admin/budgets").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void bootstrapCreatesHashedCredentialsAndDoesNotResetExistingAccounts() {
        jdbc.update("DELETE FROM admin_users");
        bootstrap.bootstrap("Primeiro@example.test", PASSWORD, "", "", false);
        String stored = jdbc.queryForObject("SELECT password_hash FROM admin_users", String.class);
        assertThat(stored).startsWith("$2a$12$");
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, stored)).isTrue();
        jdbc.update("UPDATE admin_users SET active = false");
        bootstrap.bootstrap("primeiro@example.test", "Outra-senha-123!", "", "", false);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_users", String.class)).isEqualTo(stored);
        assertThat(jdbc.queryForObject("SELECT active FROM admin_users", Boolean.class)).isFalse();
        assertThatThrownBy(() -> bootstrap.bootstrap("outro@example.test", PASSWORD, "", "", false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bootstrapInvalidDemoConfigurationDoesNotCreateAnyUser() {
        jdbc.update("DELETE FROM admin_users");
        assertThatThrownBy(() -> bootstrap.bootstrap("admin@example.test", PASSWORD, "demo@example.test", PASSWORD, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_users", Integer.class)).isZero();
    }

    private String login(String email) throws Exception {
        String response = mvc.perform(atIp(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(1800)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
    private MockHttpServletRequestBuilder atIp(MockHttpServletRequestBuilder request) {
        return request.with(servlet -> { servlet.setRemoteAddr(ip); return servlet; });
    }
    private org.springframework.test.web.servlet.ResultActions change(String token, String status, long version, String reason) throws Exception {
        String content = "{\"status\":\"" + status + "\",\"version\":" + version + (reason == null ? "" : ",\"justification\":\"" + reason + "\"") + "}";
        return mvc.perform(patch("/api/v1/admin/budgets/" + REAL_BUDGET + "/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }
    private String token(JwtEncoder issuer, String iss, String audience, Instant issued, Instant expires) {
        return issuer.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(iss).subject(ADMIN.toString()).audience(List.of(audience)).issuedAt(issued).expiresAt(expires).build())).getTokenValue();
    }
}
