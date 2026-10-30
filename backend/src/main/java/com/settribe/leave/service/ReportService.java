package com.settribe.leave.service;

import com.settribe.leave.dto.LeaveResponse;
import com.settribe.leave.entity.LeaveRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.exception.ApiException;
import com.settribe.leave.repository.LeaveRequestRepository;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepo;
    private final LeaveRequestRepository leaveRepo;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<LeaveResponse> search(String email, LocalDate fromDate, LocalDate toDate, String department,
                                      Long employeeId, LeaveType leaveType, LeaveStatus status,
                                      int page, int size, String sortBy, Sort.Direction direction) {
        validateRange(fromDate, toDate);
        User actor = currentUser(email);
        Long ownerId = actor.getRole() == Role.EMPLOYEE ? actor.getId() : null;
        Long managerId = actor.getRole() == Role.MANAGER ? actor.getId() : null;
        String allowedSort = List.of("createdAt", "fromDate", "toDate", "status").contains(sortBy)
                ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, allowedSort));
        return leaveRepo.searchReports(ownerId, managerId, blankToNull(department), employeeId, leaveType, status,
                fromDate, toDate, pageable).map(LeaveResponse::from);
    }

    @Transactional
    public byte[] exportCsv(String email, LocalDate fromDate, LocalDate toDate, String department,
                            Long employeeId, LeaveType leaveType, LeaveStatus status) {
        validateRange(fromDate, toDate);
        User actor = currentUser(email);
        Long ownerId = actor.getRole() == Role.EMPLOYEE ? actor.getId() : null;
        Long managerId = actor.getRole() == Role.MANAGER ? actor.getId() : null;
        var page = leaveRepo.searchReports(ownerId, managerId, blankToNull(department), employeeId,
                leaveType, status, fromDate, toDate, PageRequest.of(0, 10000, Sort.by("createdAt").descending()));
        if (page.getTotalElements() > 10000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Export is limited to 10,000 leave requests; narrow the filters");
        }
        StringBuilder csv = new StringBuilder("Leave request report\r\nReporting period,")
                .append(fromDate == null ? "All dates" : fromDate)
                .append(" to ").append(toDate == null ? "All dates" : toDate)
                .append("\r\nGenerated at,").append(LocalDateTime.now()).append("\r\n")
                .append("Employee Identifier,Employee,Department,Leave Type,Start Date,End Date,Status,Submitted At\r\n");
        page.getContent().forEach(leave -> appendLeave(csv, leave));
        auditService.record(email, "LEAVE_REPORT_EXPORTED", "LeaveRequest", null,
                "Filtered leave report exported as CSV", "SUCCESS");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public byte[] payrollCsv(String email, LocalDate fromDate, LocalDate toDate) {
        User actor = currentUser(email);
        requireHr(actor);
        validateRange(fromDate, toDate);
        var page = leaveRepo.searchReports(null, null, null, null, LeaveType.UNPAID, LeaveStatus.APPROVED,
                fromDate, toDate, PageRequest.of(0, 10000, Sort.by("fromDate").ascending()));
        if (page.getTotalElements() > 10000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payroll export is limited to 10,000 leave requests");
        }
        List<LeaveRequest> requests = page.getContent().stream()
                .filter(leave -> leave.getFromDate().isBefore(toDate.plusDays(1)) && !leave.getToDate().isBefore(fromDate))
                .toList();
        if (requests.stream().anyMatch(leave -> leave.getUser().getEmployeeId() == null
                || leave.getUser().getEmployeeId().isBlank())) {
            throw new ApiException(HttpStatus.CONFLICT, "Assign employee identifiers before generating the payroll export");
        }
        StringBuilder csv = new StringBuilder("Approved unpaid leave payroll report\r\nReporting period,")
                .append(fromDate).append(" to ").append(toDate)
                .append("\r\nGenerated at,").append(LocalDateTime.now()).append("\r\n")
                .append("Employee Identifier,Employee,Period Start,Period End,Unpaid Leave Days\r\n");
        for (LeaveRequest leave : requests) {
            LocalDate from = leave.getFromDate().isBefore(fromDate) ? fromDate : leave.getFromDate();
            LocalDate to = leave.getToDate().isAfter(toDate) ? toDate : leave.getToDate();
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            csv.append(csvCell(leave.getUser().getEmployeeId())).append(',')
                    .append(csvCell(leave.getUser().getName())).append(',')
                    .append(from).append(',').append(to).append(',').append(days).append("\r\n");
        }
        auditService.record(email, "PAYROLL_EXPORT_GENERATED", "LeaveRequest", null,
                "Approved unpaid leave payroll file generated", "SUCCESS");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendLeave(StringBuilder csv, LeaveRequest leave) {
        csv.append(csvCell(leave.getUser().getEmployeeId())).append(',')
                .append(csvCell(leave.getUser().getName())).append(',')
                .append(csvCell(leave.getUser().getDepartment())).append(',')
                .append(leave.getLeaveType()).append(',').append(leave.getFromDate()).append(',')
                .append(leave.getToDate()).append(',').append(leave.getStatus()).append(',')
                .append(leave.getCreatedAt()).append("\r\n");
    }

    private static String csvCell(String value) {
        if (value == null) return "";
        String safe = value;
        String formulaCandidate = safe.stripLeading();
        if (!formulaCandidate.isEmpty() && (formulaCandidate.charAt(0) == '=' || formulaCandidate.charAt(0) == '+'
                || formulaCandidate.charAt(0) == '-' || formulaCandidate.charAt(0) == '@')) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static void validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && (toDate.isBefore(fromDate)
                || ChronoUnit.DAYS.between(fromDate, toDate) > 3660)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid date range of at most ten years is required");
        }
    }

    private static void requireHr(User actor) {
        if (actor.getRole() != Role.HR && actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HR report access is required");
        }
    }

    private User currentUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
