package com.settribe.leave.dto;

import com.settribe.leave.entity.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record LeaveRequestDto(
        @NotNull(message = "Leave type is required")
        LeaveType leaveType,

        @NotNull(message = "From date is required")
        LocalDate fromDate,

        @NotNull(message = "To date is required")
        LocalDate toDate,

        @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason
) {}
