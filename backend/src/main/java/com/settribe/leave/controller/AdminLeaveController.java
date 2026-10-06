package com.settribe.leave.controller;

import com.settribe.leave.dto.LeaveResponse;
import com.settribe.leave.dto.StatusUpdateRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Access restricted to ADMIN in SecurityConfig (/api/admin/** -> hasRole("ADMIN"))
@RestController
@RequestMapping("/api/admin/leaves")
@RequiredArgsConstructor
public class AdminLeaveController {

    private final LeaveService leaveService;

    @GetMapping
    public List<LeaveResponse> listAll(@RequestParam(required = false) LeaveStatus status,
                                       @RequestParam(required = false) LeaveType type) {
        return leaveService.listAll(status, type);
    }

    @PutMapping("/{id}/status")
    public LeaveResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody StatusUpdateRequest request) {
        return leaveService.updateStatus(id, request);
    }
}
