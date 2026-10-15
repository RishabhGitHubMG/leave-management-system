package com.settribe.leave;

import com.settribe.leave.dto.LeaveRequestDto;
import com.settribe.leave.dto.StatusUpdateRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import com.settribe.leave.service.LeaveService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-characters-long",
        "app.hr-email=hr-test@example.com"
})
class LeaveEmailTests {

    private static final long WAIT_MS = 5000;

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private UserRepository userRepo;

    @Test
    void submissionEmailsEmployeeManagerAndHr() {
        User manager = createUser(Role.MANAGER, null);
        User employee = createUser(Role.EMPLOYEE, manager);

        leaveService.create(employee.getEmail(), request(5));

        List<SimpleMailMessage> sent = sentMessages(3);
        assertThat(sent).extracting(m -> m.getTo()[0])
                .containsExactlyInAnyOrder(employee.getEmail(), manager.getEmail(), "hr-test@example.com");
        assertThat(sent).allSatisfy(m -> assertThat(m.getSubject()).startsWith("Leave request submitted"));
    }

    @Test
    void rejectionEmailIncludesReviewerRemarks() {
        User manager = createUser(Role.MANAGER, null);
        User employee = createUser(Role.EMPLOYEE, manager);
        User admin = createUser(Role.ADMIN, null);
        Long leaveId = leaveService.create(employee.getEmail(), request(10)).id();
        sentMessages(3);

        leaveService.updateStatus(admin.getEmail(), leaveId,
                new StatusUpdateRequest(LeaveStatus.REJECTED, "Team is short-staffed that week"));

        List<SimpleMailMessage> sent = sentMessages(3);
        assertThat(sent).allSatisfy(m -> {
            assertThat(m.getSubject()).startsWith("Leave request rejected");
            assertThat(m.getText()).contains("Team is short-staffed that week");
        });
    }

    @Test
    void cancellationKeepsRequestAsCancelledAndEmailsStakeholders() {
        User manager = createUser(Role.MANAGER, null);
        User employee = createUser(Role.EMPLOYEE, manager);
        Long leaveId = leaveService.create(employee.getEmail(), request(15)).id();
        sentMessages(3);

        leaveService.cancel(employee.getEmail(), leaveId);

        assertThat(leaveService.listMine(employee.getEmail(), LeaveStatus.CANCELLED, null)).hasSize(1);
        List<SimpleMailMessage> sent = sentMessages(3);
        assertThat(sent).allSatisfy(m -> assertThat(m.getSubject()).startsWith("Leave request cancelled"));
    }

    @Test
    void cancelledRequestDoesNotBlockNewLeaveOnSameDates() {
        User employee = createUser(Role.EMPLOYEE, null);
        Long leaveId = leaveService.create(employee.getEmail(), request(20)).id();
        leaveService.cancel(employee.getEmail(), leaveId);

        assertThat(leaveService.create(employee.getEmail(), request(20)).status())
                .isEqualTo(LeaveStatus.PENDING);
    }

    private List<SimpleMailMessage> sentMessages(int expected) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(WAIT_MS).times(expected)).send(captor.capture());
        List<SimpleMailMessage> messages = captor.getAllValues();
        clearInvocations(mailSender);
        return messages;
    }

    private User createUser(Role role, User manager) {
        return userRepo.save(User.builder()
                .name("Test " + role)
                .email(UUID.randomUUID() + "@example.com")
                .password("not-used")
                .role(role)
                .manager(manager)
                .build());
    }

    private LeaveRequestDto request(int startInDays) {
        LocalDate from = LocalDate.now().plusDays(startInDays);
        return new LeaveRequestDto(LeaveType.ANNUAL, from, from.plusDays(1), "Family commitment needing time off");
    }
}
