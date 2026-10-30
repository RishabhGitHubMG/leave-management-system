package com.settribe.leave.service;

import com.settribe.leave.dto.EmployeeDashboardResponse;
import com.settribe.leave.dto.HrDashboardResponse;
import com.settribe.leave.dto.LeaveResponse;
import com.settribe.leave.dto.ManagerDashboardResponse;
import com.settribe.leave.entity.LeaveRequest;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.exception.ApiException;
import com.settribe.leave.repository.LeaveRequestRepository;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepo;
    private final LeaveRequestRepository leaveRepo;

    @Value("${app.timezone:UTC}")
    private String timezone;

    @Transactional(readOnly = true)
    public EmployeeDashboardResponse employee(String email) {
        User user = currentUser(email);
        List<LeaveRequest> requests = leaveRepo.findForUser(user.getId(), null, null);
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        List<LeaveResponse> upcoming = requests.stream()
                .filter(leave -> leave.getStatus() == LeaveStatus.APPROVED && !leave.getToDate().isBefore(today))
                .sorted(Comparator.comparing(LeaveRequest::getFromDate))
                .map(LeaveResponse::from).toList();
        List<LeaveResponse> recent = requests.stream().limit(10).map(LeaveResponse::from).toList();
        return new EmployeeDashboardResponse(false,
                "Leave entitlements are unavailable until an organizational leave policy is configured.",
                upcoming, recent);
    }

    @Transactional(readOnly = true)
    public ManagerDashboardResponse manager(String email) {
        User manager = currentUser(email);
        if (manager.getRole() != Role.MANAGER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Manager dashboard access is required");
        }
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);
        List<LeaveRequest> pending = leaveRepo.findForTeam(manager.getId(), LeaveStatus.PENDING, null);
        List<LeaveRequest> thisWeek = leaveRepo.findForTeamInDateRange(manager.getId(), LeaveStatus.APPROVED,
                today, weekEnd);
        List<LeaveRequest> upcoming = leaveRepo.findForTeamInDateRange(manager.getId(), LeaveStatus.APPROVED,
                today, today.plusDays(14));
        List<LeaveRequest> year = leaveRepo.findForTeamInDateRange(manager.getId(), LeaveStatus.APPROVED,
                today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()));
        List<LeaveResponse> absentToday = upcoming.stream()
                .filter(leave -> !leave.getFromDate().isAfter(today) && !leave.getToDate().isBefore(today))
                .map(LeaveResponse::from).toList();
        List<ManagerDashboardResponse.WaitingRequest> waiting = pending.stream()
                .map(leave -> new ManagerDashboardResponse.WaitingRequest(LeaveResponse.from(leave),
                        Math.max(0, ChronoUnit.DAYS.between(leave.getCreatedAt().toLocalDate(), today))))
                .toList();
        long approvedDays = year.stream().mapToLong(leave -> {
            LocalDate from = leave.getFromDate().isBefore(today.withDayOfYear(1))
                    ? today.withDayOfYear(1) : leave.getFromDate();
            LocalDate to = leave.getToDate().isAfter(today.withDayOfYear(today.lengthOfYear()))
                    ? today.withDayOfYear(today.lengthOfYear()) : leave.getToDate();
            return ChronoUnit.DAYS.between(from, to) + 1;
        }).sum();
        return new ManagerDashboardResponse(waiting, absentToday,
                thisWeek.stream().map(LeaveResponse::from).toList(),
                upcoming.stream().map(LeaveResponse::from).toList(), conflicts(upcoming),
                Math.toIntExact(userRepo.countByManager_IdAndActiveTrue(manager.getId())), approvedDays);
    }

    @Transactional(readOnly = true)
    public HrDashboardResponse hr(String email, LocalDate fromDate, LocalDate toDate,
                                  String department, LeaveType leaveType) {
        User viewer = currentUser(email);
        if (viewer.getRole() != Role.HR && viewer.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HR dashboard access is required");
        }
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)
                || ChronoUnit.DAYS.between(fromDate, toDate) > 3660) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid date range of at most ten years is required");
        }
        String departmentFilter = department == null || department.isBlank() ? null : department.trim();
        Map<String, Long> departments = new LinkedHashMap<>();
        leaveRepo.countByDepartmentInRange(LeaveStatus.APPROVED, fromDate, toDate, departmentFilter, leaveType).forEach(row ->
                departments.put(row[0] == null ? "Unassigned" : row[0].toString(), ((Number) row[1]).longValue()));
        Map<String, Long> leaveTypes = new LinkedHashMap<>();
        leaveRepo.countByLeaveTypeInRange(LeaveStatus.APPROVED, fromDate, toDate, departmentFilter, leaveType).forEach(row ->
                leaveTypes.put(row[0].toString(), ((Number) row[1]).longValue()));
        Map<String, Long> statuses = new LinkedHashMap<>();
        leaveRepo.countByStatusInRange(fromDate, toDate, departmentFilter, leaveType).forEach(row ->
                statuses.put(row[0].toString(), ((Number) row[1]).longValue()));
        Map<String, Long> months = new LinkedHashMap<>();
        leaveRepo.countByMonthInRange(LeaveStatus.APPROVED, fromDate, toDate, departmentFilter, leaveType).forEach(row ->
                months.put(String.format("%04d-%02d", ((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                        ((Number) row[2]).longValue()));
        return new HrDashboardResponse(fromDate, toDate, departments, leaveTypes, statuses, months);
    }

    private List<ManagerDashboardResponse.LeaveConflict> conflicts(List<LeaveRequest> requests) {
        List<ManagerDashboardResponse.LeaveConflict> conflicts = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            LeaveRequest first = requests.get(i);
            for (int j = i + 1; j < requests.size(); j++) {
                LeaveRequest second = requests.get(j);
                LocalDate overlapFrom = first.getFromDate().isAfter(second.getFromDate())
                        ? first.getFromDate() : second.getFromDate();
                LocalDate overlapTo = first.getToDate().isBefore(second.getToDate())
                        ? first.getToDate() : second.getToDate();
                if (!overlapFrom.isAfter(overlapTo)) {
                    conflicts.add(new ManagerDashboardResponse.LeaveConflict(first.getId(), first.getUser().getName(),
                            second.getId(), second.getUser().getName(), overlapFrom, overlapTo));
                }
            }
        }
        return conflicts;
    }

    private User currentUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
