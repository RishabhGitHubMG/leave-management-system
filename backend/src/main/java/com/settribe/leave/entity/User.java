package com.settribe.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * JPA Entity mapping the 'users' database table.
 *
 * Stores user profiles, credentials (BCrypt hashed), roles (EMPLOYEE or ADMIN),
 * and extended professional profile information collected at registration.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * Primary Key auto-incremented by the database engine.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Full name of the employee or administrator.
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Unique email address used for login and notifications.
     */
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /**
     * Secure one-way BCrypt hash of the user's password.
     * Never stores plain-text passwords.
     */
    @Column(nullable = false)
    private String password;

    /**
     * Role of the user in the system (EMPLOYEE or ADMIN).
     * Mapped as a VARCHAR string in the database.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.EMPLOYEE;

    /**
     * Department the user belongs to (e.g., Engineering & Tech, Human Resources).
     * Optional — may be null for legacy or admin accounts.
     */
    @Column(length = 100)
    private String department;

    /**
     * Job title or designation of the employee (e.g., Senior Developer, Team Lead).
     * Optional — may be null for legacy or admin accounts.
     */
    @Column(length = 100)
    private String designation;

    /**
     * Contact phone number for the employee.
     * Optional — stored as a string to support international formats.
     */
    @Column(name = "phone_number", length = 25)
    private String phoneNumber;

    /**
     * Unique internal employee identifier assigned at registration.
     * Auto-generated in format "SET-XXXX" if not provided by the user.
     */
    @Column(name = "employee_id", length = 50)
    private String employeeId;

    /**
     * Deactivated accounts cannot log in or use existing tokens. Rows are never deleted,
     * so leave history stays linked to the user.
     */
    @Column(name = "active", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean active = true;

    @Column(name = "failed_login_attempts", nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * Timestamp when the user account was created.
     * Automatically populated by Hibernate upon insertion and never updated.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
