package com.settribe.leave.dto;

import com.settribe.leave.entity.LeaveStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object (DTO) for admin status updates on an employee's leave request.
 *
 * @param status       Target status decision (must be APPROVED or REJECTED).
 * @param adminRemarks Optional explanatory remarks from the administrator.
 *                     MANDATORY when status is REJECTED — must be at least 5 characters.
 */
public record StatusUpdateRequest(
        @NotNull(message = "Status is required")
        LeaveStatus status,

        String adminRemarks
) {}
