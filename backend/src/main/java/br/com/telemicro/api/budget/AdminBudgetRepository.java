package br.com.telemicro.api.budget;

import static br.com.telemicro.api.budget.AdminBudgetModels.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class AdminBudgetRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String SELECT = "SELECT b.*, s.code AS service_code, s.name AS service_name FROM budget_requests b JOIN service_types s ON s.id = b.service_type_id";
    private static final RowMapper<BudgetItem> BUDGET = (rs, row) -> new BudgetItem(
            rs.getObject("id", UUID.class), rs.getString("protocol"), rs.getString("customer_name"), rs.getString("phone"),
            rs.getString("service_code"), rs.getString("service_name"), rs.getString("message"),
            BudgetStatus.valueOf(rs.getString("status")), rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    public AdminBudgetRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public BudgetPage list(BudgetFilter filter, boolean demoOnly) {
        Query query = filters(filter, demoOnly);
        Long total = jdbc.queryForObject("SELECT count(*) FROM budget_requests b JOIN service_types s ON s.id = b.service_type_id" + query.where(), query.params(), Long.class);
        query.params().addValue("limit", filter.pageSize()).addValue("offset", (long) filter.pageNumber() * filter.pageSize());
        String direction = filter.order() == BudgetFilter.Sort.NEWEST ? "DESC" : "ASC";
        List<BudgetItem> rows = jdbc.query(SELECT + query.where() + " ORDER BY b.created_at " + direction + ", b.id " + direction + " LIMIT :limit OFFSET :offset", query.params(), BUDGET);
        long count = total == null ? 0 : total;
        return new BudgetPage(rows, filter.pageNumber(), filter.pageSize(), count, (count + filter.pageSize() - 1) / filter.pageSize());
    }

    public Summary summary(BudgetFilter filter, boolean demoOnly) {
        Query query = filters(filter, demoOnly);
        Map<BudgetStatus, Long> counts = new EnumMap<>(BudgetStatus.class);
        for (BudgetStatus status : BudgetStatus.values()) counts.put(status, 0L);
        jdbc.query("SELECT b.status, count(*) AS total FROM budget_requests b JOIN service_types s ON s.id = b.service_type_id" + query.where() + " GROUP BY b.status",
                query.params(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> counts.put(BudgetStatus.valueOf(rs.getString("status")), rs.getLong("total")));
        List<ServiceCount> services = jdbc.query(
                "SELECT s.code, s.name, count(*) AS total FROM budget_requests b JOIN service_types s ON s.id = b.service_type_id"
                        + query.where() + " GROUP BY s.code, s.name ORDER BY total DESC, s.name",
                query.params(), (rs, row) -> new ServiceCount(rs.getString("code"), rs.getString("name"), rs.getLong("total")));
        return new Summary(counts, counts.values().stream().mapToLong(Long::longValue).sum(), services);
    }

    public Optional<BudgetItem> find(UUID id, boolean demoOnly, boolean lock) {
        return jdbc.query(SELECT + " WHERE b.id = :id" + (demoOnly ? " AND b.is_demo = true" : "") + (lock ? " FOR UPDATE OF b" : ""),
                Map.of("id", id), BUDGET).stream().findFirst();
    }

    public List<HistoryItem> history(UUID id) {
        return jdbc.query("""
                SELECT h.*, u.email AS author_email FROM budget_status_history h
                LEFT JOIN admin_users u ON u.id = h.changed_by
                WHERE h.budget_request_id = :id ORDER BY h.created_at, h.id
                """, Map.of("id", id), (rs, row) -> new HistoryItem(rs.getObject("id", UUID.class),
                rs.getString("previous_status") == null ? null : BudgetStatus.valueOf(rs.getString("previous_status")),
                BudgetStatus.valueOf(rs.getString("new_status")), rs.getObject("changed_by", UUID.class),
                rs.getString("author_email"), rs.getTimestamp("created_at").toInstant()));
    }

    public List<NoteItem> notes(UUID id) {
        return jdbc.query("""
                SELECT n.*, u.email AS author_email FROM budget_notes n JOIN admin_users u ON u.id = n.author_id
                WHERE n.budget_request_id = :id ORDER BY n.created_at, n.id
                """, Map.of("id", id), (rs, row) -> new NoteItem(rs.getObject("id", UUID.class),
                rs.getObject("author_id", UUID.class), rs.getString("author_email"), rs.getString("text"),
                rs.getTimestamp("created_at").toInstant()));
    }

    public int updateStatus(UUID id, long version, BudgetStatus status, Instant now) {
        return jdbc.update("UPDATE budget_requests SET status = :status, version = version + 1, updated_at = :now WHERE id = :id AND version = :version",
                Map.of("id", id, "version", version, "status", status.name(), "now", Timestamp.from(now)));
    }
    public void addHistory(UUID id, BudgetStatus previous, BudgetStatus next, UUID actor, Instant now) {
        jdbc.update("""
                INSERT INTO budget_status_history(budget_request_id, previous_status, new_status, changed_by, created_at)
                VALUES (:id, :previous, :next, :actor, :now)
                """, Map.of("id", id, "previous", previous.name(), "next", next.name(), "actor", actor, "now", Timestamp.from(now)));
    }
    public UUID addNote(UUID id, UUID actor, String text, Instant now) {
        UUID noteId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO budget_notes(id, budget_request_id, author_id, text, created_at)
                VALUES (:noteId, :id, :actor, :text, :now)
                """, Map.of("noteId", noteId, "id", id, "actor", actor, "text", text, "now", Timestamp.from(now)));
        return noteId;
    }

    private Query filters(BudgetFilter filter, boolean demoOnly) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (demoOnly) sql.append(" AND b.is_demo = true");
        if (filter.status() != null) { sql.append(" AND b.status = :status"); params.addValue("status", filter.status().name()); }
        if (filter.serviceCode() != null && !filter.serviceCode().isBlank()) {
            sql.append(" AND s.code = :serviceCode"); params.addValue("serviceCode", filter.serviceCode().strip());
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String search = filter.q().strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            sql.append(" AND (b.customer_name ILIKE :search ESCAPE '\\' OR b.protocol ILIKE :search ESCAPE '\\')");
            params.addValue("search", "%" + search + "%");
        }
        if (filter.from() != null) { sql.append(" AND b.created_at >= :from"); params.addValue("from", Timestamp.from(filter.from())); }
        if (filter.to() != null) { sql.append(" AND b.created_at < :to"); params.addValue("to", Timestamp.from(filter.to())); }
        return new Query(sql.toString(), params);
    }
    private record Query(String where, MapSqlParameterSource params) {}
}
