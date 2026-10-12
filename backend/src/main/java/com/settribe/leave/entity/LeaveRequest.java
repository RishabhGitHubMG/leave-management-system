package com.settribe.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Entity mapping the 'leave_requests' database table.
 * 
 * Represents an individual employee leave application, tracking dates, reason,
 * leave category, approval status, and the applicant User.
 */
@Entity
@Table(name = "leave_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    /**
     * Primary Key auto-incremented by the database engine.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Many-to-One relationship linking the leave request to the applicant User.
     * FetchType.LAZY ensures user details are loaded only when explicitly requested,
     * avoiding unnecessary database queries.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Category of leave: SICK, CASUAL, ANNUAL, or UNPAID.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType leaveType;

    /**
     * Starting date of the requested leave period (inclusive).
     */
    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    /**
     * Ending date of the requested leave period (inclusive).
     */
    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    /**
     * Reason provided by the employee explaining the leave request.
     */
    @Column(nullable = false, length = 500)
    private String reason;

    /**
     * Current workflow approval status: PENDING, APPROVED, or REJECTED.
     * New requests always initialize to PENDING.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    /**
     * Timestamp when the leave request was submitted.
     * Automatically populated by Hibernate.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

