package com.settribe.leave;

import com.settribe.leave.repository.LeaveRequestRepository;
import com.settribe.leave.repository.NotificationRepository;
import com.settribe.leave.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-characters-long",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchemaScriptTests {

    private static final String URL = SchemaScriptSupport.newUrl("schema_validation")
            .replace("DB_CLOSE_DELAY=-1", "DB_CLOSE_DELAY=0");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private LeaveRequestRepository leaveRepo;

    @Autowired
    private NotificationRepository notificationRepo;

    @Test
    void flywayCreatesEveryMappedTableAndHibernateValidatesIt() {
        assertThat(userRepo.findAll()).isEmpty();
        assertThat(leaveRepo.findAll()).isEmpty();
        assertThat(notificationRepo.findAll()).isEmpty();
    }
}
