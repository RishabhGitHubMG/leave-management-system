package com.settribe.leave.dto;

import com.settribe.leave.entity.LeaveStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object (DTO) for admin status updates on an employee's leave request.
 * 
 * @param status Target status decision (must be APPROVED or REJECTED).
 */
public record StatusUpdateRequest(
        @NotNull(message = "Status is required")
        LeaveStatus status
) {}

