package com.settribe.leave.dto;

import com.settribe.leave.entity.LeaveStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull(message = "Status is required")
        LeaveStatus status
) {}
