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
        "app.jwt.secret=test-secret-for-disposable-mysql-migration-tests",
        "spring.flyway.enabled=true",
        "spring.flyway.clean-disabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(named = "MYSQL_MIGRATION_CI", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MySqlMigrationTests {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("MYSQL_TEST_URL"));
        registry.add("spring.datasource.username", () -> requiredEnv("MYSQL_TEST_USER"));
        registry.add("spring.datasource.password", () -> requiredEnv("MYSQL_TEST_PASSWORD"));
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
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"
                        + " AND table_name IN ('users', 'leave_requests', 'notifications')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE()",
                String.class)).isEqualTo("utf8mb4");
        assertThat(jdbc.queryForObject(
                "SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE()",
                String.class)).isEqualTo("utf8mb4_unicode_ci");
    }

    @Test
    void mysqlRejectsRoleOutsideEntityEnum() {
        String email = email();
        assertMysqlError(3819, () -> insertUser(email, "SUPERUSER"));
    }

    @Test
    void mysqlRejectsLeaveStatusOutsideEntityEnum() {
        long userId = insertUser(email(), "EMPLOYEE");
        assertMysqlError(3819, () -> insertLeave(userId, "PENDING_REVIEW", "2030-01-01", "2030-01-02"));
    }

    @Test
    void mysqlRejectsInvertedLeaveDates() {
        long userId = insertUser(email(), "EMPLOYEE");
        assertMysqlError(3819, () -> insertLeave(userId, "PENDING", "2030-01-02", "2030-01-01"));
    }

    @Test
    void mysqlRejectsDuplicateEmail() {
        String email = email();
        insertUser(email, "EMPLOYEE");
        assertMysqlError(1062, () -> insertUser(email, "EMPLOYEE"));
    }

    @Test
    void mysqlRejectsOrphanedLeaveNotificationAndManagerReferences() {
        assertMysqlError(1452, () -> insertLeave(Long.MAX_VALUE, "PENDING", "2030-01-01", "2030-01-02"));
        assertMysqlError(1452, () -> jdbc.update(
                "INSERT INTO notifications (user_id, message) VALUES (?, 'message')", Long.MAX_VALUE));
        assertMysqlError(1452, () -> jdbc.update(
                "INSERT INTO users (name, email, password, role, manager_id) VALUES ('orphan', ?, 'hash', 'EMPLOYEE', ?)",
                email(), Long.MAX_VALUE));
    }

    private long insertUser(String email, String role) {
        jdbc.update("INSERT INTO users (name, email, password, role) VALUES ('test', ?, 'hash', ?)", email, role);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertLeave(long userId, String status, String fromDate, String toDate) {
        jdbc.update("INSERT INTO leave_requests (user_id, leave_type, from_date, to_date, reason, status)"
                        + " VALUES (?, 'SICK', ?, ?, 'test', ?)",
                userId, fromDate, toDate, status);
    }

    private void assertMysqlError(int expectedErrorCode, Runnable operation) {
        DataAccessException failure = assertThrows(DataAccessException.class, operation::run);
        SQLException sqlException = findSqlException(failure);
        assertNotNull(sqlException);
        assertThat(sqlException.getErrorCode()).isEqualTo(expectedErrorCode);
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
            throw new IllegalStateException(name + " must be set for the MySQL migration test");
        }
        return value;
    }

    private static String email() {
        return UUID.randomUUID() + "@mysql-migration.test";
    }
}
