package com.settribe.leave;

import com.settribe.leave.entity.LeaveRequest;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.LeaveRequestRepository;
import com.settribe.leave.repository.UserRepository;
import com.settribe.leave.service.AdminAccountUpdateService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-characters-long",
        "app.admin.email=",
        "app.admin.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class AdminAccountUpdateTests {

    @Autowired
    private AdminAccountUpdateService accountUpdateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Test
    void updatesOnlyTargetAdminAndPreservesItsIdentityAndLeaveRecords() {
        String suffix = UUID.randomUUID().toString();
        User admin = createUser("admin-" + suffix + "@example.com", Role.ADMIN, "old-hash");
        User other = createUser("other-" + suffix + "@example.com", Role.EMPLOYEE, "other-hash");
        LeaveRequest leave = leaveRequestRepository.saveAndFlush(LeaveRequest.builder()
                .user(admin)
                .leaveType(LeaveType.ANNUAL)
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2026, 4, 2))
                .reason("Annual leave")
                .build());
        Long adminId = admin.getId();
        Long leaveId = leave.getId();

        accountUpdateService.updateExistingAdmin(adminId, admin.getEmail(),
                "  New-" + suffix + "@Example.com  ", "new-password-123");
        entityManager.clear();

        User updated = userRepository.findById(adminId).orElseThrow();
        User unchanged = userRepository.findById(other.getId()).orElseThrow();
        LeaveRequest preservedLeave = leaveRequestRepository.findById(leaveId).orElseThrow();
        assertThat(updated.getId()).isEqualTo(adminId);
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
        assertThat(updated.getEmail()).isEqualTo("new-" + suffix + "@example.com");
        assertThat(passwordEncoder.matches("new-password-123", updated.getPassword())).isTrue();
        assertThat(updated.getPassword()).isNotEqualTo("new-password-123");
        assertThat(unchanged.getEmail()).isEqualTo("other-" + suffix + "@example.com");
        assertThat(unchanged.getPassword()).isEqualTo("other-hash");
        assertThat(preservedLeave.getUser().getId()).isEqualTo(adminId);
        assertThat(leaveRequestRepository.findForUser(adminId, null, null))
                .extracting(LeaveRequest::getId)
                .containsExactly(leaveId);
    }

    @Test
    void rejectsEmailOwnedByAnotherAccountWithoutChangingTheTarget() {
        String suffix = UUID.randomUUID().toString();
        User admin = createUser("admin-" + suffix + "@example.com", Role.ADMIN, "old-hash");
        User other = createUser("other-" + suffix + "@example.com", Role.EMPLOYEE, "other-hash");

        assertThatThrownBy(() -> accountUpdateService.updateExistingAdmin(admin.getId(), admin.getEmail(),
                other.getEmail(), "new-password-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The new email address is already assigned to another account.");
        entityManager.clear();

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getEmail()).isEqualTo(admin.getEmail());
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getPassword()).isEqualTo("old-hash");
    }

    @Test
    void rejectsMismatchedCurrentEmailAndNonAdminTargets() {
        String suffix = UUID.randomUUID().toString();
        User admin = createUser("admin-" + suffix + "@example.com", Role.ADMIN, "old-hash");
        User employee = createUser("employee-" + suffix + "@example.com", Role.EMPLOYEE, "employee-hash");

        assertThatThrownBy(() -> accountUpdateService.updateExistingAdmin(admin.getId(),
                "different-" + suffix + "@example.com", "new-admin@example.com", "new-password-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The target ID and expected current email do not match.");
        assertThatThrownBy(() -> accountUpdateService.updateExistingAdmin(employee.getId(), employee.getEmail(),
                "new-employee@example.com", "new-password-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The target account is not an active ADMIN account.");
    }

    private User createUser(String email, Role role, String passwordHash) {
        return userRepository.saveAndFlush(User.builder()
                .name("Update Test User")
                .email(email)
                .password(passwordHash)
                .role(role)
                .build());
    }
}
