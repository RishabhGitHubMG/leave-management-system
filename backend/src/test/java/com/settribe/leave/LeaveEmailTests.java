package com.settribe.leave;

import com.settribe.leave.dto.LeaveRequestDto;
import com.settribe.leave.dto.StatusUpdateRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.exception.ApiException;
import com.settribe.leave.repository.UserRepository;
import com.settribe.leave.service.LeaveService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-characters-long",
        "app.hr-email=hr-test@example.com",
        "spring.mail.username=sender-test@example.com",
        "spring.mail.password=test-only-password"
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

    @Test
    void approvalEmailNamesEmployeeLeaveTypeAndDates() {
        User manager = createUser(Role.MANAGER, null);
        User employee = createUser(Role.EMPLOYEE, manager);
        LeaveRequestDto dto = request(30);
        Long leaveId = leaveService.create(employee.getEmail(), dto).id();
        sentMessages(3);

        leaveService.updateStatus(manager.getEmail(), leaveId,
                new StatusUpdateRequest(LeaveStatus.APPROVED, null));

        List<SimpleMailMessage> sent = sentMessages(3);
        assertThat(sent).allSatisfy(m -> {
            assertThat(m.getSubject()).startsWith("Leave request approved - Test EMPLOYEE");
            assertThat(m.getText()).contains("Test EMPLOYEE", "annual", dto.fromDate().toString(),
                    dto.toDate().toString(), "was approved");
        });
    }

    @Test
    void cancelledRequestCannotBeApprovedOrCancelledAgain() {
        User manager = createUser(Role.MANAGER, null);
        User employee = createUser(Role.EMPLOYEE, manager);
        Long leaveId = leaveService.create(employee.getEmail(), request(35)).id();
        leaveService.cancel(employee.getEmail(), leaveId);
        sentMessages(6);

        assertThatThrownBy(() -> leaveService.updateStatus(manager.getEmail(), leaveId,
                new StatusUpdateRequest(LeaveStatus.APPROVED, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only pending requests can be approved or rejected");
        assertThatThrownBy(() -> leaveService.cancel(employee.getEmail(), leaveId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only pending requests can be cancelled");

        assertThat(leaveService.listMine(employee.getEmail(), LeaveStatus.CANCELLED, null)).hasSize(1);
        verifyNoInteractions(mailSender);
    }

    @Test
    void mailFailureDoesNotRollBackSubmission() {
        doThrow(new MailSendException("smtp unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        try {
            User employee = createUser(Role.EMPLOYEE, null);
            leaveService.create(employee.getEmail(), request(40));

            verify(mailSender, timeout(WAIT_MS).times(2)).send(any(SimpleMailMessage.class));
            assertThat(leaveService.listMine(employee.getEmail(), LeaveStatus.PENDING, null)).hasSize(1);
        } finally {
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));
            clearInvocations(mailSender);
        }
    }

    @Test
    void managerWhoIsAlsoHrReceivesSingleEmail() {
        User manager = createUserWithEmail(Role.MANAGER, null, "HR-Test@example.com");
        User employee = createUser(Role.EMPLOYEE, manager);

        leaveService.create(employee.getEmail(), request(45));

        List<SimpleMailMessage> sent = sentMessages(2);
        assertThat(sent).extracting(m -> m.getTo()[0].toLowerCase())
                .containsExactlyInAnyOrder(employee.getEmail(), "hr-test@example.com");
    }

    private List<SimpleMailMessage> sentMessages(int expected) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(WAIT_MS).times(expected)).send(captor.capture());
        List<SimpleMailMessage> messages = captor.getAllValues();
        clearInvocations(mailSender);
        return messages;
    }

    private User createUser(Role role, User manager) {
        return createUserWithEmail(role, manager, UUID.randomUUID() + "@example.com");
    }

    private User createUserWithEmail(Role role, User manager, String email) {
        return userRepo.save(User.builder()
                .name("Test " + role)
                .email(email)
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
