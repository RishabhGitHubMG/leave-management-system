package com.settribe.leave.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/register", "/dashboard", "/admin/users", "/settings/notifications"})
    public String index() {
        return "forward:/index.html";
    }
}
