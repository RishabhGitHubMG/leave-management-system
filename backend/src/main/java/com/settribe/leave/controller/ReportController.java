package com.settribe.leave.controller;

import com.settribe.leave.dto.LeaveResponse;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping({"/leaves", "/leave-statement", "/department"})
    public Page<LeaveResponse> leaves(Authentication auth,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                      @RequestParam(required = false) String department,
                                      @RequestParam(required = false) Long employeeId,
                                      @RequestParam(required = false) LeaveType leaveType,
                                      @RequestParam(required = false) LeaveStatus status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size,
                                      @RequestParam(defaultValue = "createdAt") String sortBy,
                                      @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid report pagination");
        }
        return reportService.search(auth.getName(), fromDate, toDate, department, employeeId, leaveType,
                status, page, size, sortBy, direction);
    }

    @GetMapping(value = "/leaves.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(Authentication auth,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                         @RequestParam(required = false) String department,
                                         @RequestParam(required = false) Long employeeId,
                                         @RequestParam(required = false) LeaveType leaveType,
                                         @RequestParam(required = false) LeaveStatus status) {
        byte[] file = reportService.exportCsv(auth.getName(), fromDate, toDate, department,
                employeeId, leaveType, status);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=leave-report.csv")
                .body(file);
    }

    @GetMapping(value = "/payroll/unpaid-leave.csv", produces = "text/csv")
    public ResponseEntity<byte[]> payroll(Authentication auth,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] file = reportService.payrollCsv(auth.getName(), fromDate, toDate);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=unpaid-leave-payroll.csv")
                .body(file);
    }
}
