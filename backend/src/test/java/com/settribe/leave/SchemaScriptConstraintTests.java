package com.settribe.leave;

import com.settribe.leave.entity.EmploymentType;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.Role;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// H2 mode cannot verify MySQL collation or version-specific CHECK enforcement.
class SchemaScriptConstraintTests {

    @Test
    void migrationAcceptsEveryEnumValueProducedByEntities() throws SQLException {
        try (var database = SchemaScriptSupport.openMigrated("enum_values")) {
            Connection c = database.connection();
            for (Role role : Role.values()) {
                insertUser(c, role.name(), null);
            }
            for (EmploymentType type : EmploymentType.values()) {
                insertUser(c, "EMPLOYEE", type.name());
            }
            long userId = insertUser(c, "EMPLOYEE", null);
            for (LeaveType type : LeaveType.values()) {
                for (LeaveStatus status : LeaveStatus.values()) {
                    insertLeave(c, userId, type.name(), status.name());
                }
            }
            insertNotification(c, userId);
        }
    }

    @Test
    void migrationRejectsInvalidEnumsAndOrphanedForeignKeys() throws SQLException {
        try (var database = SchemaScriptSupport.openMigrated("invalid_values")) {
            Connection c = database.connection();
            long userId = insertUser(c, "EMPLOYEE", null);
            long managerId = insertUser(c, "MANAGER", null);
            insertUser(c, "EMPLOYEE", null, managerId);

            assertThatCode(() -> insertLeave(c, userId, "SICK", "PENDING")).doesNotThrowAnyException();
            assertThatThrownBy(() -> insertUser(c, "SUPERUSER", null)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertUser(c, "EMPLOYEE", "PART_TIME")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertLeave(c, userId, "SICK", "ON_HOLD")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertLeave(c, userId, "PARENTAL", "PENDING")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertLeave(c, userId + 1000, "SICK", "PENDING")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertNotification(c, userId + 1000)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertUser(c, "EMPLOYEE", null, userId + 1000)).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void migrationEnforcesLeaveDateOrderAndUniqueEmail() throws SQLException {
        try (var database = SchemaScriptSupport.openMigrated("integrity_constraints")) {
            Connection c = database.connection();
            String email = "unique." + UUID.randomUUID() + "@schema.test";
            long userId = insertUser(c, email, "EMPLOYEE", null, null);
            assertThatThrownBy(() -> insertUser(c, email, "EMPLOYEE", null, null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertLeave(c, userId, "SICK", "PENDING", "2030-01-02", "2030-01-01"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void migrationPreservesDatabaseDefaultsAndNamedIndexes() throws SQLException {
        try (var database = SchemaScriptSupport.openMigrated("defaults_and_indexes")) {
            Connection c = database.connection();
            long userId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users (name, email, password) VALUES ('n', ?, 'hash')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "defaults." + UUID.randomUUID() + "@schema.test");
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    userId = keys.getLong(1);
                }
            }

            try (Statement statement = c.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT role, active, failed_login_attempts FROM users WHERE id=" + userId)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("role")).isEqualTo("EMPLOYEE");
                assertThat(rs.getBoolean("active")).isTrue();
                assertThat(rs.getInt("failed_login_attempts")).isZero();
            }

            long leaveId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO leave_requests (user_id, leave_type, from_date, to_date, reason)"
                            + " VALUES (?, 'SICK', DATE '2030-01-01', DATE '2030-01-02', 'reason')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, userId);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    leaveId = keys.getLong(1);
                }
            }
            try (Statement statement = c.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT status, created_at FROM leave_requests WHERE id=" + leaveId)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("PENDING");
                assertThat(rs.getTimestamp("created_at")).isNotNull();
            }

            long notificationId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO notifications (user_id, message) VALUES (?, 'message')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, userId);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    notificationId = keys.getLong(1);
                }
            }
            try (Statement statement = c.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT is_read, created_at FROM notifications WHERE id=" + notificationId)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("is_read")).isFalse();
                assertThat(rs.getTimestamp("created_at")).isNotNull();
            }

            assertThat(indexNames(c, "users")).contains("idx_users_manager");
            assertThat(indexNames(c, "leave_requests")).contains("idx_leave_user", "idx_leave_status");
            assertThat(indexNames(c, "notifications")).contains("idx_notification_user_read");
        }
    }

    private static long insertUser(Connection c, String role, String employmentType) throws SQLException {
        String email = role + "." + employmentType + "." + UUID.randomUUID() + "@schema.test";
        return insertUser(c, email, role, employmentType, null);
    }

    private static long insertUser(Connection c, String role, String employmentType, long managerId)
            throws SQLException {
        String email = role + "." + employmentType + "." + UUID.randomUUID() + "@schema.test";
        return insertUser(c, email, role, employmentType, managerId);
    }

    private static long insertUser(Connection c, String email, String role, String employmentType, Long managerId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (name, email, password, role, employment_type, manager_id) VALUES ('n', ?, 'hash', ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, role);
            ps.setString(3, employmentType);
            if (managerId == null) {
                ps.setNull(4, java.sql.Types.BIGINT);
            } else {
                ps.setLong(4, managerId);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void insertLeave(Connection c, long userId, String type, String status) throws SQLException {
        insertLeave(c, userId, type, status, "2030-01-01", "2030-01-02");
    }

    private static void insertLeave(Connection c, long userId, String type, String status, String from, String to)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO leave_requests (user_id, leave_type, from_date, to_date, reason, status)"
                        + " VALUES (?, ?, ?, ?, 'reason', ?)") ) {
            ps.setLong(1, userId);
            ps.setString(2, type);
            ps.setDate(3, java.sql.Date.valueOf(from));
            ps.setDate(4, java.sql.Date.valueOf(to));
            ps.setString(5, status);
            ps.executeUpdate();
        }
    }

    private static void insertNotification(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO notifications (user_id, message, is_read) VALUES (?, 'message', FALSE)")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    private static Set<String> indexNames(Connection c, String table) throws SQLException {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (ResultSet rs = c.getMetaData().getIndexInfo(null, null, table.toUpperCase(), false, false)) {
            while (rs.next()) {
                names.add(rs.getString("INDEX_NAME"));
            }
        }
        return names;
    }
}
