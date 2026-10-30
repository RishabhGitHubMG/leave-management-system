package com.settribe.leave.controller;

import com.settribe.leave.dto.EmployeeDashboardResponse;
import com.settribe.leave.dto.HrDashboardResponse;
import com.settribe.leave.dto.ManagerDashboardResponse;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/employee")
    public EmployeeDashboardResponse employee(Authentication auth) {
        return dashboardService.employee(auth.getName());
    }

    @GetMapping("/manager")
    public ManagerDashboardResponse manager(Authentication auth) {
        return dashboardService.manager(auth.getName());
    }

    @GetMapping("/hr")
    public HrDashboardResponse hr(Authentication auth,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                  @RequestParam(required = false) String department,
                                  @RequestParam(required = false) LeaveType leaveType) {
        LocalDate end = toDate == null ? LocalDate.now() : toDate;
        LocalDate start = fromDate == null ? end.minusYears(1) : fromDate;
        return dashboardService.hr(auth.getName(), start, end, department, leaveType);
    }
}
