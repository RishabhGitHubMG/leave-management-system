package com.settribe.leave;

import com.settribe.leave.repository.LeaveRequestRepository;
import com.settribe.leave.repository.NotificationRepository;
import com.settribe.leave.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the application with ddl-auto=validate against a database built from sql/schema.sql,
 * so Hibernate fails startup if any mapped table or column is missing or has an incompatible type.
 */
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
class SchemaScriptTests {

    private static final String URL =
            "jdbc:h2:mem:schema_validation;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        try (Connection connection = SchemaScriptSupport.open(URL)) {
            SchemaScriptSupport.apply(connection);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare schema validation database", e);
        }
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private LeaveRequestRepository leaveRepo;

    @Autowired
    private NotificationRepository notificationRepo;

    @Test
    void everyMappedTableAndColumnIsQueryableAfterValidation() {
        assertThat(userRepo.findAll()).isEmpty();
        assertThat(leaveRepo.findAll()).isEmpty();
        assertThat(notificationRepo.findAll()).isEmpty();
    }
}
