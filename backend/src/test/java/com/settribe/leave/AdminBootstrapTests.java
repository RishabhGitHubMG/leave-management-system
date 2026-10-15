package com.settribe.leave;

import com.settribe.leave.config.AdminInitializer;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-characters-long",
        "app.admin.email=bootstrap-admin-test@example.com",
        "app.admin.password=bootstrap-test-password"
})
@AutoConfigureMockMvc
class AdminBootstrapTests {

    private static final String ADMIN_EMAIL = "bootstrap-admin-test@example.com";
    private static final String ADMIN_PASSWORD = "bootstrap-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AdminInitializer adminInitializer;

    @Test
    void configuredAdminCanLogInAndReceivesAdminRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void rerunningInitializerDoesNotRecreateAdmin() {
        User before = userRepo.findByEmail(ADMIN_EMAIL).orElseThrow();

        adminInitializer.run();

        User after = userRepo.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
    }

    @Test
    void registrationCannotClaimAdminEmailAndNewAccountsAreEmployees() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(ADMIN_EMAIL)))
                .andExpect(status().isConflict());

        String employeeEmail = "employee-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(employeeEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(employeeEmail, "employee-pass-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String registerJson(String email) {
        return "{\"name\":\"Test Employee\",\"email\":\"" + email
                + "\",\"password\":\"employee-pass-123\"}";
    }
}
