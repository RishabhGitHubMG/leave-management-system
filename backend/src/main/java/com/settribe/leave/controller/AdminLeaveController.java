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

/**
 * Controller handling administrator operations for employee leave requests.
 * 
 * Access to all endpoints under /api/admin/** is strictly restricted to
 * users having the ADMIN role (configured in SecurityConfig).
 * 
 * Endpoints:
 * - GET /api/admin/leaves            : List all employee leaves with optional filters.
 * - PUT /api/admin/leaves/{id}/status: Approve or Reject a pending leave request.
 */
@RestController
@RequestMapping("/api/admin/leaves")
@RequiredArgsConstructor
public class AdminLeaveController {

    private final LeaveService leaveService;

    /**
     * Lists all leave requests across the company for admin review.
     * 
     * @param status Optional filter by status (PENDING, APPROVED, REJECTED).
     * @param type   Optional filter by leave type (SICK, CASUAL, ANNUAL, UNPAID).
     * @return List of all LeaveResponse items.
     */
    @GetMapping
    public List<LeaveResponse> listAll(@RequestParam(required = false) LeaveStatus status,
                                       @RequestParam(required = false) LeaveType type) {
        return leaveService.listAll(status, type);
    }

    /**
     * Updates the status of a pending leave request to APPROVED or REJECTED.
     * 
     * @param id      ID of the leave request.
     * @param request Validated status payload containing the new status (APPROVED or REJECTED).
     * @return Updated LeaveResponse.
     */
    @PutMapping("/{id}/status")
    public LeaveResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody StatusUpdateRequest request) {
        return leaveService.updateStatus(id, request);
    }
}

