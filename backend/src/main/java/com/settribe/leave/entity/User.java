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
 * Stores user profiles, credentials (BCrypt hashed), and roles (EMPLOYEE or ADMIN).
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
     * Timestamp when the user account was created.
     * Automatically populated by Hibernate upon insertion and never updated.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

