package com.settribe.leave;

import com.settribe.leave.entity.EmploymentType;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.Role;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs sql/schema.sql on one H2 connection and checks that every enum value the entities can
 * produce is accepted, while values outside the enum sets and orphaned foreign keys are rejected.
 */
class SchemaScriptConstraintTests {

    @Test
    void schemaAcceptsEveryEnumValueProducedByEntities() throws SQLException {
        try (Connection c = open()) {
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
    void schemaRejectsValuesOutsideEnumSetsAndOrphans() throws SQLException {
        try (Connection c = open()) {
            long userId = insertUser(c, "EMPLOYEE", null);
            assertThatCode(() -> insertLeave(c, userId, "SICK", "PENDING")).doesNotThrowAnyException();

            assertThatThrownBy(() -> insertUser(c, "SUPERUSER", null)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertUser(c, "EMPLOYEE", "PART_TIME")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertLeave(c, userId, "SICK", "ON_HOLD")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertLeave(c, userId, "PARENTAL", "PENDING")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertLeave(c, userId + 1000, "SICK", "PENDING")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertNotification(c, userId + 1000)).isInstanceOf(SQLException.class);
        }
    }

    private static Connection open() throws SQLException {
        String url = "jdbc:h2:mem:schema_constraints_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL";
        Connection connection = SchemaScriptSupport.open(url);
        SchemaScriptSupport.apply(connection);
        return connection;
    }

    private static long insertUser(Connection c, String role, String employmentType) throws SQLException {
        String email = role + "." + employmentType + "." + UUID.randomUUID() + "@schema.test";
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (name, email, password, role, employment_type) VALUES ('n', ?, 'hash', ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, role);
            ps.setString(3, employmentType);
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void insertLeave(Connection c, long userId, String type, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO leave_requests (user_id, leave_type, from_date, to_date, reason, status, admin_remarks)"
                        + " VALUES (?, ?, DATE '2030-01-01', DATE '2030-01-02', 'reason', ?, 'remarks')")) {
            ps.setLong(1, userId);
            ps.setString(2, type);
            ps.setString(3, status);
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
}
