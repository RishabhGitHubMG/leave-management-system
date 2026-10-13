package com.settribe.leave.controller;

import com.settribe.leave.dto.LeaveResponse;
import com.settribe.leave.dto.StatusUpdateRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Leave review endpoints for MANAGER (direct reports), HR and ADMIN (all employees).
 * Access is enforced in SecurityConfig, and the per-request scope is enforced in LeaveService.
 *
 * Endpoints:
 * - GET /api/admin/leaves            : List leave requests the caller may review, with optional filters.
 * - PUT /api/admin/leaves/{id}/status: Approve or Reject a pending leave request.
 */
@RestController
@RequestMapping("/api/admin/leaves")
@RequiredArgsConstructor
public class AdminLeaveController {

    private final LeaveService leaveService;

    @GetMapping
    public List<LeaveResponse> listAll(Authentication auth,
                                       @RequestParam(required = false) LeaveStatus status,
                                       @RequestParam(required = false) LeaveType type) {
        return leaveService.listReviewable(auth.getName(), status, type);
    }

    @PutMapping("/{id}/status")
    public LeaveResponse updateStatus(Authentication auth,
                                      @PathVariable Long id,
                                      @Valid @RequestBody StatusUpdateRequest request) {
        return leaveService.updateStatus(auth.getName(), id, request);
    }
}
