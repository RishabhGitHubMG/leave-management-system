package com.settribe.leave;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
class LeaveApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void inspectCheckConstraints() {
        System.out.println(jdbcTemplate.queryForList(
                "SELECT CONSTRAINT_NAME, CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS"));
    }

}
