package com.settribe.leave;

import com.jayway.jsonpath.JsonPath;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
@AutoConfigureMockMvc
class LeaveAccessRegressionTests {

    private static final String PASSWORD = "employee-pass-123";
    private static final String AUTH = "Authorization";

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void protectedEndpointRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/leaves")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotUseAdminUserEndpoints() throws Exception {
        String token = tokenFor(staffEmail(Role.EMPLOYEE));

        mockMvc.perform(get("/api/admin/users").header(AUTH, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotEditOrCancelAnotherEmployeesLeave() throws Exception {
        String ownerToken = tokenFor(staffEmail(Role.EMPLOYEE));
        String intruderToken = tokenFor(staffEmail(Role.EMPLOYEE));
        long leaveId = submitLeave(ownerToken, 50);

        mockMvc.perform(put("/api/leaves/{id}", leaveId)
                        .header(AUTH, bearer(intruderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leaveJson(50)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/leaves/{id}", leaveId).header(AUTH, bearer(intruderToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownAccountAndWrongPasswordReturnTheSameLoginError() throws Exception {
        String existingEmail = staffEmail(Role.EMPLOYEE);
        String unknownEmail = "nobody-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(existingEmail, "wrong-password-999")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(unknownEmail, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void rejectionRemarksLongerThanColumnAreRejectedAsValidationError() throws Exception {
        String adminToken = tokenFor(staffEmail(Role.ADMIN));
        long leaveId = submitLeave(tokenFor(staffEmail(Role.EMPLOYEE)), 55);

        mockMvc.perform(put("/api/admin/leaves/{id}/status", leaveId)
                        .header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"adminRemarks\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerCannotReviewLeaveOfEmployeeOutsideTheirTeam() throws Exception {
        String managerToken = tokenFor(staffEmail(Role.MANAGER));
        long leaveId = submitLeave(tokenFor(staffEmail(Role.EMPLOYEE)), 60);

        mockMvc.perform(put("/api/admin/leaves/{id}/status", leaveId)
                        .header(AUTH, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvedLeaveCannotBeEditedAndKeepsApprovedStatus() throws Exception {
        String employeeToken = tokenFor(staffEmail(Role.EMPLOYEE));
        String adminToken = tokenFor(staffEmail(Role.ADMIN));
        long leaveId = submitLeave(employeeToken, 65);

        mockMvc.perform(put("/api/admin/leaves/{id}/status", leaveId)
                        .header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/leaves/{id}", leaveId)
                        .header(AUTH, bearer(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leaveJson(65)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/leaves").param("status", "APPROVED").header(AUTH, bearer(employeeToken)))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void profileNameRejectsDigitsAndWhitespaceOnly() throws Exception {
        String token = tokenFor(staffEmail(Role.EMPLOYEE));

        for (String name : List.of("Jane 2", "   ")) {
            mockMvc.perform(put("/api/users/me")
                            .header(AUTH, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + name + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/api/users/me")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane Doe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    void adminCreatedUserNameRejectsDigits() throws Exception {
        String adminToken = tokenFor(staffEmail(Role.ADMIN));

        mockMvc.perform(post("/api/admin/users")
                        .header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin 2\",\"email\":\"" + UUID.randomUUID()
                                + "@example.com\",\"password\":\"password-123\",\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isBadRequest());
    }

    private String staffEmail(Role role) {
        String email = "regression-" + UUID.randomUUID() + "@example.com";
        userRepo.save(User.builder()
                .name("Regression " + role.name().toLowerCase())
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .build());
        return email;
    }

    private String tokenFor(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private long submitLeave(String token, int daysAhead) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/leaves")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leaveJson(daysAhead)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String leaveJson(int daysAhead) {
        LocalDate from = LocalDate.now().plusDays(daysAhead);
        return "{\"leaveType\":\"ANNUAL\",\"fromDate\":\"" + from + "\",\"toDate\":\"" + from.plusDays(1)
                + "\",\"reason\":\"Family commitment needing time off\"}";
    }
}
