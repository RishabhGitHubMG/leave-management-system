package com.settribe.leave.dto;

import java.time.LocalDate;
import java.util.List;

public record ManagerDashboardResponse(
        List<WaitingRequest> pendingApprovals,
        List<LeaveResponse> absentToday,
        List<LeaveResponse> expectedThisWeek,
        List<LeaveResponse> upcomingTeamLeave,
        List<LeaveConflict> overlapWarnings,
        int teamSize,
        long approvedTeamDaysThisYear) {

    public record WaitingRequest(LeaveResponse request, long waitingDays) {
    }

    public record LeaveConflict(Long firstRequestId, String firstEmployee, Long secondRequestId,
                                String secondEmployee, LocalDate overlapFrom, LocalDate overlapTo) {
    }
}
