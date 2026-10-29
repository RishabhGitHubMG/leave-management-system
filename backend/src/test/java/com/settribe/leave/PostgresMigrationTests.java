package com.settribe.leave;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-for-disposable-postgres-migration-tests",
        "spring.flyway.enabled=true",
        "spring.flyway.clean-disabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(named = "POSTGRES_MIGRATION_CI", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresMigrationTests {

    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("POSTGRES_TEST_URL"));
        registry.add("spring.datasource.username", () -> requiredEnv("POSTGRES_TEST_USER"));
        registry.add("spring.datasource.password", () -> requiredEnv("POSTGRES_TEST_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void v1MigratesFreshSchemaAndHibernateValidatesMappings() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema()"
                        + " AND table_name IN ('users', 'leave_requests', 'notifications')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT pg_encoding_to_char(encoding) FROM pg_database WHERE datname = current_database()",
                String.class)).isEqualTo("UTF8");
    }

    @Test
    void checkConstraintRejectsRoleOutsideEntityEnum() {
        assertSqlState(CHECK_VIOLATION, () -> insertUser(email(), "SUPERUSER"));
    }

    @Test
    void checkConstraintRejectsLeaveStatusOutsideEntityEnum() {
        long userId = insertUser(email(), "EMPLOYEE");
        assertSqlState(CHECK_VIOLATION, () -> insertLeave(userId, "PENDING_REVIEW", "2030-01-01", "2030-01-02"));
    }

    @Test
    void checkConstraintRejectsInvertedLeaveDates() {
        long userId = insertUser(email(), "EMPLOYEE");
        assertSqlState(CHECK_VIOLATION, () -> insertLeave(userId, "PENDING", "2030-01-02", "2030-01-01"));
    }

    @Test
    void uniqueConstraintRejectsDuplicateEmail() {
        String email = email();
        insertUser(email, "EMPLOYEE");
        assertSqlState(UNIQUE_VIOLATION, () -> insertUser(email, "EMPLOYEE"));
    }

    @Test
    void foreignKeysRejectOrphanedLeaveNotificationAndManagerReferences() {
        assertSqlState(FOREIGN_KEY_VIOLATION, () -> insertLeave(Long.MAX_VALUE, "PENDING", "2030-01-01", "2030-01-02"));
        assertSqlState(FOREIGN_KEY_VIOLATION, () -> jdbc.update(
                "INSERT INTO notifications (user_id, message) VALUES (?, 'message')", Long.MAX_VALUE));
        assertSqlState(FOREIGN_KEY_VIOLATION, () -> jdbc.update(
                "INSERT INTO users (name, email, password, role, manager_id) VALUES ('orphan', ?, 'hash', 'EMPLOYEE', ?)",
                email(), Long.MAX_VALUE));
    }

    private long insertUser(String email, String role) {
        jdbc.update("INSERT INTO users (name, email, password, role) VALUES ('test', ?, 'hash', ?)", email, role);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertLeave(long userId, String status, String fromDate, String toDate) {
        jdbc.update("INSERT INTO leave_requests (user_id, leave_type, from_date, to_date, reason, status)"
                        + " VALUES (?, 'SICK', CAST(? AS DATE), CAST(? AS DATE), 'test', ?)",
                userId, fromDate, toDate, status);
    }

    private void assertSqlState(String expectedSqlState, Runnable operation) {
        DataAccessException failure = assertThrows(DataAccessException.class, operation::run);
        SQLException sqlException = findSqlException(failure);
        assertNotNull(sqlException);
        assertThat(sqlException.getSQLState()).isEqualTo(expectedSqlState);
    }

    private static SQLException findSqlException(Throwable error) {
        Throwable cause = error;
        while (cause != null && !(cause instanceof SQLException)) {
            cause = cause.getCause();
        }
        return (SQLException) cause;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the PostgreSQL migration test");
        }
        return value;
    }

    private static String email() {
        return UUID.randomUUID() + "@postgres-migration.test";
    }
}
