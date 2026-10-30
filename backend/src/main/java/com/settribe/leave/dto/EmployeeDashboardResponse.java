package com.settribe.leave.dto;

import java.util.List;

public record EmployeeDashboardResponse(
        boolean balanceAvailable,
        String balanceMessage,
        List<LeaveResponse> upcomingApprovedLeave,
        List<LeaveResponse> recentRequests) {
}
