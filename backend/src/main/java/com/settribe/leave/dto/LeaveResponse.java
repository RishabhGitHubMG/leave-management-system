package com.settribe.leave.dto;

import com.settribe.leave.entity.LeaveRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) representing a leave request returned to frontend clients.
 *
 * Flattens the nested User entity into convenient fields for straightforward display
 * on employee and admin dashboards, and includes admin remarks for rejected/approved
 * requests and extended user profile metadata.
 */
public record LeaveResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        // Extended user profile fields — shown on admin view leave cards
        String userDepartment,
        String userDesignation,
        String userEmployeeId,
        // Leave details
        LeaveType leaveType,
        LocalDate fromDate,
        LocalDate toDate,
        String reason,
        LeaveStatus status,
        // Admin remarks — populated on REJECTED (mandatory) or APPROVED (optional) decisions
        String adminRemarks,
        LocalDateTime createdAt
) {
    /**
     * Static factory method converting a JPA LeaveRequest entity into an immutable LeaveResponse DTO.
     * Safely handles null user profile fields for legacy or admin accounts.
     */
    public static LeaveResponse from(LeaveRequest l) {
        return new LeaveResponse(
                l.getId(),
                l.getUser().getId(),
                l.getUser().getName(),
                l.getUser().getEmail(),
                l.getUser().getDepartment(),
                l.getUser().getDesignation(),
                l.getUser().getEmployeeId(),
                l.getLeaveType(),
                l.getFromDate(),
                l.getToDate(),
                l.getReason(),
                l.getStatus(),
                l.getAdminRemarks(),
                l.getCreatedAt());
    }
}
