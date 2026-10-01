package br.com.telemicro.api.budget;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** SQL específico do PostgreSQL para serializar retentativas e tratar colisões sem abortar a transação. */
@Repository
public class BudgetWriteRepository {
    private final JdbcTemplate jdbc;
    public BudgetWriteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockKey(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, key);
    }

    public Optional<PreviousRequest> findPrevious(String key, Instant now) {
        jdbc.update("DELETE FROM budget_idempotency WHERE request_key = ? AND expires_at <= ?", key, Timestamp.from(now));
        return jdbc.query("""
                SELECT i.payload_hash, b.protocol, b.created_at
                FROM budget_idempotency i JOIN budget_requests b ON b.id = i.budget_request_id
                WHERE i.request_key = ?
                """, (rs, row) -> new PreviousRequest(rs.getString("payload_hash"),
                new BudgetCreatedResponse(rs.getString("protocol"), rs.getTimestamp("created_at").toInstant())), key)
                .stream().findFirst();
    }

    public boolean insertBudget(UUID id, String protocol, String name, String phone, UUID serviceId,
            String message, Instant now) {
        return jdbc.update("""
                INSERT INTO budget_requests(id, protocol, customer_name, phone, service_type_id, message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (protocol) DO NOTHING
                """, id, protocol, name, phone, serviceId, message, Timestamp.from(now), Timestamp.from(now)) == 1;
    }

    public void insertInitialHistory(UUID budgetId, Instant now) {
        jdbc.update("""
                INSERT INTO budget_status_history(budget_request_id, new_status, created_at)
                VALUES (?, 'NEW', ?)
                """, budgetId, Timestamp.from(now));
    }

    public void remember(String key, String hash, UUID budgetId, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO budget_idempotency(request_key, payload_hash, budget_request_id, expires_at)
                VALUES (?, ?, ?, ?)
                """, key, hash, budgetId, Timestamp.from(expiresAt));
    }

    public record PreviousRequest(String payloadHash, BudgetCreatedResponse response) {}
}
