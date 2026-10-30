package com.settribe.leave;

import com.settribe.leave.dto.LeaveRequestDto;
import com.settribe.leave.dto.StatusUpdateRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.exception.ApiException;
import com.settribe.leave.repository.UserRepository;
import com.settribe.leave.service.DashboardService;
import com.settribe.leave.service.LeaveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
class DashboardTests {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private UserRepository userRepo;

    @Test
    void employeeDashboardUsesOnlyAuthenticatedEmployeesRequestsAndNoInventedBalances() {
        String employee = createUser(Role.EMPLOYEE, null, "Product");
        String other = createUser(Role.EMPLOYEE, null, "Finance");
        String admin = createUser(Role.ADMIN, null, null);
        Long own = submit(employee);
        submit(other);
        leaveService.updateStatus(admin, own, new StatusUpdateRequest(LeaveStatus.APPROVED, null));

        var dashboard = dashboardService.employee(employee);

        assertThat(dashboard.balanceAvailable()).isFalse();
        assertThat(dashboard.upcomingApprovedLeave()).extracting("id").containsExactly(own);
        assertThat(dashboard.recentRequests()).hasSize(1);
    }

    @Test
    void managerDashboardContainsDirectReportsOnly() {
        String manager = createUser(Role.MANAGER, null, "Product");
        String employee = createUser(Role.EMPLOYEE, manager, "Product");
        String unrelated = createUser(Role.EMPLOYEE, null, "Finance");
        String admin = createUser(Role.ADMIN, null, null);
        Long directLeave = submit(employee);
        submit(unrelated);
        leaveService.updateStatus(admin, directLeave, new StatusUpdateRequest(LeaveStatus.APPROVED, null));

        var dashboard = dashboardService.manager(manager);

        assertThat(dashboard.teamSize()).isEqualTo(1);
        assertThat(dashboard.upcomingTeamLeave()).extracting("userEmail").containsExactly(employee);
    }

    @Test
    void employeeCannotOpenManagerDashboard() {
        String employee = createUser(Role.EMPLOYEE, null, "Product");
        assertThatThrownBy(() -> dashboardService.manager(employee))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void hrAggregatesApprovedRequestsByDepartmentAndType() {
        String department = "Product-" + UUID.randomUUID();
        String employee = createUser(Role.EMPLOYEE, null, department);
        String admin = createUser(Role.ADMIN, null, null);
        Long requestId = submit(employee, LeaveType.SICK);
        leaveService.updateStatus(admin, requestId, new StatusUpdateRequest(LeaveStatus.APPROVED, null));

        var dashboard = dashboardService.hr(admin, LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                department, LeaveType.SICK);

        assertThat(dashboard.requestsByDepartment()).containsEntry(department, 1L);
        assertThat(dashboard.requestsByLeaveType()).containsEntry("SICK", 1L);
    }

    private Long submit(String email) {
        return submit(email, LeaveType.ANNUAL);
    }

    private Long submit(String email, LeaveType type) {
        LocalDate from = LocalDate.now().plusDays(2);
        return leaveService.create(email, new LeaveRequestDto(type, from, from.plusDays(1),
                "Approved leave request for dashboard test")).id();
    }

    private String createUser(Role role, String managerEmail, String department) {
        User manager = managerEmail == null ? null : userRepo.findByEmail(managerEmail).orElseThrow();
        String email = UUID.randomUUID() + "@example.com";
        return userRepo.save(User.builder()
                .name("Dashboard " + role)
                .email(email)
                .password("not-used")
                .role(role)
                .department(department)
                .manager(manager)
                .build()).getEmail();
    }
}
