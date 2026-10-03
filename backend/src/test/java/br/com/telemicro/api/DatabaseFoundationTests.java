package br.com.telemicro.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class DatabaseFoundationTests {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.9-bookworm");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsCreateSchemaAndCatalogWithoutAdministrativeCredentials() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForList("SELECT name FROM service_types ORDER BY display_order", String.class))
                .containsExactly("Assistência técnica", "Equipamentos e acessórios", "Recarga de cartuchos e toner",
                        "Suporte presencial ou online", "Locação de equipamentos", "Manutenção preventiva", "Outro assunto");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_requests", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_status_history", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_notes", Integer.class)).isZero();
    }

    @Test
    void databaseRejectsInvalidStatusAndMissingServiceReference() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO budget_requests(protocol, customer_name, phone, service_type_id, status)
                SELECT 'INVALID-STATUS', 'Teste', '33999999999', id, 'INVALID' FROM service_types LIMIT 1
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO budget_requests(protocol, customer_name, phone, service_type_id)
                VALUES ('INVALID-SERVICE', 'Teste', '33999999999', '00000000-0000-0000-0000-000000000000')
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
