package br.com.telemicro.api.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.RowMapper;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AdminUserRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<AdminUser> MAPPER = (rs, row) -> new AdminUser(
            rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"),
            rs.getString("role"), rs.getBoolean("active"));

    public AdminUserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<AdminUser> findByEmail(String email) {
        return jdbc.query("SELECT * FROM admin_users WHERE email = ?", MAPPER, email).stream().findFirst();
    }
    public Optional<AdminUser> findById(UUID id) {
        return jdbc.query("SELECT * FROM admin_users WHERE id = ?", MAPPER, id).stream().findFirst();
    }
    public void lockBootstrap() {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('telemicro-bootstrap', 0))", rs -> {});
    }
    public boolean hasAdmin() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM admin_users WHERE role = 'ADMIN')", Boolean.class));
    }
    public void create(String email, String hash, String role) {
        jdbc.update("INSERT INTO admin_users(email, password_hash, role) VALUES (?, ?, ?)", email, hash, role);
    }
}
