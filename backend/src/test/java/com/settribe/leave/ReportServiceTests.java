package com.settribe.leave;

import com.settribe.leave.dto.LeaveRequestDto;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import com.settribe.leave.service.LeaveService;
import com.settribe.leave.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
class ReportServiceTests {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private ReportService reportService;

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private UserRepository userRepo;

    @Test
    void employeeReportIsRestrictedToAuthenticatedOwner() {
        User owner = createUser(Role.EMPLOYEE, "SAFE-1", "Employee");
        User other = createUser(Role.EMPLOYEE, "SAFE-2", "Other");
        createLeave(owner, LeaveType.ANNUAL);
        createLeave(other, LeaveType.SICK);

        var page = reportService.search(owner.getEmail(), null, null, null, other.getId(), null, null,
                0, 25, "createdAt", Sort.Direction.DESC);

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void reportCsvEscapesFormulaCells() {
        User employee = createUser(Role.EMPLOYEE, "SAFE-3", "=HYPERLINK(\"https://example.test\")");
        createLeave(employee, LeaveType.ANNUAL);

        String csv = new String(reportService.exportCsv(employee.getEmail(), null, null, null,
                null, null, null), StandardCharsets.UTF_8);

        assertThat(csv).contains("'=HYPERLINK(\"\"https://example.test\"" );
    }

    @Test
    void payrollExportRejectsUnauthorizedUsers() {
        User employee = createUser(Role.EMPLOYEE, "SAFE-4", "Employee");
        assertThatThrownBy(() -> reportService.payrollCsv(employee.getEmail(), LocalDate.now(), LocalDate.now()))
                .hasMessageContaining("HR report access");
    }

    private User createUser(Role role, String employeeId, String name) {
        return userRepo.save(User.builder()
                .name(name)
                .email(UUID.randomUUID() + "@example.com")
                .password("not-used")
                .role(role)
                .employeeId(employeeId)
                .build());
    }

    private void createLeave(User user, LeaveType type) {
        LocalDate from = LocalDate.now().plusDays(3);
        leaveService.create(user.getEmail(), new LeaveRequestDto(type, from, from.plusDays(1),
                "Report test request with adequate reason"));
    }
}
