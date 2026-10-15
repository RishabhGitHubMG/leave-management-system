package com.settribe.leave.controller;

import com.settribe.leave.dto.LeaveRequestDto;
import com.settribe.leave.dto.LeaveResponse;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller handling employee leave management operations.
 * 
 * Endpoints:
 * - GET    /api/leaves      : List current employee's leaves with optional status/type filters.
 * - POST   /api/leaves      : Submit a new leave application.
 * - PUT    /api/leaves/{id} : Update an existing PENDING leave application.
 * - DELETE /api/leaves/{id} : Cancel/delete an existing PENDING leave application.
 */
@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    /**
     * Retrieves all leave applications submitted by the authenticated user.
     * 
     * @param auth   Current authentication object (auth.getName() returns the user's email).
     * @param status Optional filter for leave status (PENDING, APPROVED, REJECTED).
     * @param type   Optional filter for leave type (SICK, CASUAL, ANNUAL, UNPAID).
     * @return List of LeaveResponse objects.
     */
    @GetMapping
    public List<LeaveResponse> list(Authentication auth,
                                    @RequestParam(required = false) LeaveStatus status,
                                    @RequestParam(required = false) LeaveType type) {
        return leaveService.listMine(auth.getName(), status, type);
    }

    /**
     * Submits a new leave request for the authenticated user.
     * 
     * @param auth Current authentication object.
     * @param dto  Validated leave request payload (leaveType, fromDate, toDate, reason).
     * @return HTTP 201 Created with the persisted LeaveResponse.
     */
    @PostMapping
    public ResponseEntity<LeaveResponse> create(Authentication auth,
                                                @Valid @RequestBody LeaveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveService.create(auth.getName(), dto));
    }

    /**
     * Updates an existing pending leave request owned by the authenticated user.
     * 
     * @param auth Current authentication object.
     * @param id   ID of the leave request to update.
     * @param dto  Validated updated leave details.
     * @return Updated LeaveResponse.
     */
    @PutMapping("/{id}")
    public LeaveResponse update(Authentication auth, @PathVariable Long id,
                                @Valid @RequestBody LeaveRequestDto dto) {
        return leaveService.update(auth.getName(), id, dto);
    }

    /**
     * Cancels a pending leave request owned by the authenticated user. The request is kept with CANCELLED status.
     *
     * @param auth Current authentication object.
     * @param id   ID of the leave request to cancel.
     * @return HTTP 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(Authentication auth, @PathVariable Long id) {
        leaveService.cancel(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
