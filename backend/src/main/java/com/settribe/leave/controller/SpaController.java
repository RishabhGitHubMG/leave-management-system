package com.settribe.leave.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/register", "/dashboard", "/dashboard/manager", "/dashboard/analytics", "/admin/users", "/admin/audit", "/reports", "/reports/payroll", "/settings/notifications"})
    public String index() {
        return "forward:/index.html";
    }
}
