package com.settribe.leave.dto;

import java.time.LocalDate;
import java.util.Map;

public record HrDashboardResponse(
        LocalDate startDate,
        LocalDate endDate,
        Map<String, Long> requestsByDepartment,
        Map<String, Long> requestsByLeaveType,
        Map<String, Long> requestsByStatus,
        Map<String, Long> monthlyApprovedRequests) {
}
