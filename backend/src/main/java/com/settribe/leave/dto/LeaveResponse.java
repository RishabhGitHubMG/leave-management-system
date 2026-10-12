package com.settribe.leave.dto;

import com.settribe.leave.entity.LeaveRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) representing a leave request returned to frontend clients.
 * 
 * Flattens the nested User entity into convenient fields (userId, userName, userEmail)
 * for straightforward display on employee and admin dashboards.
 */
public record LeaveResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        LeaveType leaveType,
        LocalDate fromDate,
        LocalDate toDate,
        String reason,
        LeaveStatus status,
        LocalDateTime createdAt
) {
    /**
     * Static factory method converting a JPA LeaveRequest entity into an immutable LeaveResponse DTO.
     */
    public static LeaveResponse from(LeaveRequest l) {
        return new LeaveResponse(
                l.getId(),
                l.getUser().getId(),
                l.getUser().getName(),
                l.getUser().getEmail(),
                l.getLeaveType(),
                l.getFromDate(),
                l.getToDate(),
                l.getReason(),
                l.getStatus(),
                l.getCreatedAt());
    }
}

