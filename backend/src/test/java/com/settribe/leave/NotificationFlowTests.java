package com.settribe.leave;

import com.settribe.leave.dto.LeaveRequestDto;
import com.settribe.leave.dto.NotificationResponse;
import com.settribe.leave.dto.StatusUpdateRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.exception.ApiException;
import com.settribe.leave.repository.UserRepository;
import com.settribe.leave.service.LeaveService;
import com.settribe.leave.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
class NotificationFlowTests {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepo;

    @Test
    void approvalCreatesUnreadNotificationForApplicant() {
        String employeeEmail = createUser(Role.EMPLOYEE);
        String adminEmail = createUser(Role.ADMIN);
        Long leaveId = submitLeave(employeeEmail, LeaveType.ANNUAL);

        leaveService.updateStatus(adminEmail, leaveId, new StatusUpdateRequest(LeaveStatus.APPROVED, null));

        assertThat(notificationService.unreadCount(employeeEmail).count()).isEqualTo(1);
        NotificationResponse notification = notificationService.listMine(employeeEmail).get(0);
        assertThat(notification.message()).contains("annual leave").contains("approved");
        assertThat(notification.read()).isFalse();
    }

    @Test
    void rejectionCreatesNotificationForApplicant() {
        String employeeEmail = createUser(Role.EMPLOYEE);
        String adminEmail = createUser(Role.ADMIN);
        Long leaveId = submitLeave(employeeEmail, LeaveType.SICK);

        leaveService.updateStatus(adminEmail, leaveId,
                new StatusUpdateRequest(LeaveStatus.REJECTED, "Insufficient cover this week"));

        assertThat(notificationService.listMine(employeeEmail).get(0).message()).contains("rejected");
    }

    @Test
    void markingOneReadDecrementsUnreadCount() {
        String employeeEmail = createUser(Role.EMPLOYEE);
        String adminEmail = createUser(Role.ADMIN);
        Long leaveId = submitLeave(employeeEmail, LeaveType.CASUAL);
        leaveService.updateStatus(adminEmail, leaveId, new StatusUpdateRequest(LeaveStatus.APPROVED, null));
        Long notificationId = notificationService.listMine(employeeEmail).get(0).id();

        NotificationResponse marked = notificationService.markRead(employeeEmail, notificationId);

        assertThat(marked.read()).isTrue();
        assertThat(notificationService.unreadCount(employeeEmail).count()).isZero();
    }

    @Test
    void markAllReadClearsUnreadCount() {
        String employeeEmail = createUser(Role.EMPLOYEE);
        String adminEmail = createUser(Role.ADMIN);
        Long first = submitLeave(employeeEmail, LeaveType.UNPAID, 5);
        Long second = submitLeave(employeeEmail, LeaveType.CASUAL, 20);
        leaveService.updateStatus(adminEmail, first, new StatusUpdateRequest(LeaveStatus.APPROVED, null));
        leaveService.updateStatus(adminEmail, second, new StatusUpdateRequest(LeaveStatus.REJECTED, "Team is short-staffed"));
        assertThat(notificationService.unreadCount(employeeEmail).count()).isEqualTo(2);

        notificationService.markAllRead(employeeEmail);

        assertThat(notificationService.unreadCount(employeeEmail).count()).isZero();
    }

    @Test
    void cannotMarkAnotherUsersNotificationAsRead() {
        String employeeEmail = createUser(Role.EMPLOYEE);
        String otherEmail = createUser(Role.EMPLOYEE);
        String adminEmail = createUser(Role.ADMIN);
        Long leaveId = submitLeave(employeeEmail, LeaveType.ANNUAL);
        leaveService.updateStatus(adminEmail, leaveId, new StatusUpdateRequest(LeaveStatus.APPROVED, null));
        Long notificationId = notificationService.listMine(employeeEmail).get(0).id();

        assertThatThrownBy(() -> notificationService.markRead(otherEmail, notificationId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notificationService.unreadCount(employeeEmail).count()).isEqualTo(1);
    }

    private String createUser(Role role) {
        String email = UUID.randomUUID() + "@example.com";
        userRepo.save(User.builder()
                .name("Test User")
                .email(email)
                .password("not-used")
                .role(role)
                .build());
        return email;
    }

    private Long submitLeave(String email, LeaveType type) {
        return submitLeave(email, type, 5);
    }

    private Long submitLeave(String email, LeaveType type, int startInDays) {
        LocalDate from = LocalDate.now().plusDays(startInDays);
        return leaveService.create(email, new LeaveRequestDto(type, from, from.plusDays(1),
                "Family commitment needing time off")).id();
    }
}
