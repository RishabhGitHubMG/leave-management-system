package com.settribe.leave.service;

import com.settribe.leave.dto.LeaveRequestDto;
import com.settribe.leave.dto.LeaveResponse;
import com.settribe.leave.dto.StatusUpdateRequest;
import com.settribe.leave.entity.LeaveRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.entity.User;
import com.settribe.leave.exception.ApiException;
import com.settribe.leave.repository.LeaveRequestRepository;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service encapsulating core business rules and workflow for leave applications.
 * 
 * Features:
 * - Employee operations: Create, read, edit, and cancel personal leave requests.
 * - Admin operations: View all company requests, approve or reject requests.
 * - Security protections: Ownership validation, ID enumeration defense, state validation.
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    private static final List<LeaveStatus> INACTIVE_STATUSES = List.of(LeaveStatus.REJECTED, LeaveStatus.CANCELLED);

    // =========================================================================
    // Employee Workflow Methods
    // =========================================================================

    /**
     * Lists all leave requests belonging to the currently authenticated employee.
     * Supports optional filtering by leave status or type.
     */
    @Transactional(readOnly = true)
    public List<LeaveResponse> listMine(String email, LeaveStatus status, LeaveType type) {
        User user = currentUser(email);
        return leaveRepo.findForUser(user.getId(), status, type)
                .stream().map(LeaveResponse::from).toList();
    }

    /**
     * Creates a new leave request in PENDING status.
     * 
     * @param email Email of the requesting user extracted from JWT token.
     * @param dto   Validated leave parameters.
     * @return Saved LeaveResponse.
     */
    @Transactional
    public LeaveResponse create(String email, LeaveRequestDto dto) {
        validateDates(dto); // Enforce that toDate is on or after fromDate and within bounds
        User user = userRepo.findByEmailForUpdate(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Prevent overlapping active leave requests for the same employee
        if (leaveRepo.hasOverlappingLeave(user.getId(), dto.fromDate(), dto.toDate(), null, INACTIVE_STATUSES)) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have an active leave request overlapping this date range");
        }

        LeaveRequest leave = LeaveRequest.builder()
                .user(user)
                .leaveType(dto.leaveType())
                .fromDate(dto.fromDate())
                .toDate(dto.toDate())
                .reason(dto.reason().trim())
                .status(LeaveStatus.PENDING) // New applications always start as PENDING
                .build();
        LeaveRequest saved = leaveRepo.save(leave);
        notificationService.notifyLeaveSubmission(saved);
        auditService.record(email, "LEAVE_SUBMITTED", "LeaveRequest", saved.getId().toString(),
                "Leave request submitted", "SUCCESS");
        LeaveEmailEvent event = emailEvent(LeaveEmailEvent.Kind.SUBMITTED, saved, null);
        emailService.enqueueLeaveEvent(event);
        eventPublisher.publishEvent(event);
        return LeaveResponse.from(saved);
    }

    /**
     * Updates an existing leave request.
     * 
     * Note: Employees can only edit requests that are still in PENDING status.
     * If the request is already APPROVED or REJECTED, an exception is thrown.
     */
    @Transactional
    public LeaveResponse update(String email, Long id, LeaveRequestDto dto) {
        validateDates(dto);
        userRepo.findByEmailForUpdate(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        LeaveRequest leave = findOwnPending(email, id, "edited");

        // Prevent updating into an overlapping date range with another existing request
        if (leaveRepo.hasOverlappingLeave(leave.getUser().getId(), dto.fromDate(), dto.toDate(), leave.getId(), INACTIVE_STATUSES)) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have another active leave request overlapping this date range");
        }

        leave.setLeaveType(dto.leaveType());
        leave.setFromDate(dto.fromDate());
        leave.setToDate(dto.toDate());
        leave.setReason(dto.reason().trim());
        return LeaveResponse.from(leaveRepo.save(leave));
    }


    /**
     * Cancels an existing pending leave request. The row is kept with CANCELLED status for history.
     *
     * Note: Once a request is APPROVED or REJECTED, it cannot be cancelled by the employee.
     */
    @Transactional
    public void cancel(String email, Long id) {
        LeaveRequest leave = findOwnPending(email, id, "cancelled");
        LeaveEmailEvent event = emailEvent(LeaveEmailEvent.Kind.CANCELLED, leave, null);
        if (leaveRepo.updatePendingStatus(id, LeaveStatus.CANCELLED, null, LeaveStatus.PENDING) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending requests can be cancelled");
        }
        auditService.record(email, "LEAVE_CANCELLED", "LeaveRequest", id.toString(),
                "Pending leave request cancelled", "SUCCESS");
        emailService.enqueueLeaveEvent(event);
        eventPublisher.publishEvent(event);
    }

    // =========================================================================
    // Admin Workflow Methods
    // =========================================================================

    /**
     * Lists leave requests the reviewer is allowed to act on: all requests for HR and ADMIN,
     * only direct reports for MANAGER.
     */
    @Transactional(readOnly = true)
    public List<LeaveResponse> listReviewable(String email, LeaveStatus status, LeaveType type) {
        User reviewer = currentUser(email);
        List<LeaveRequest> items = switch (reviewer.getRole()) {
            case ADMIN, HR -> leaveRepo.findAllFiltered(status, type);
            case MANAGER -> leaveRepo.findForTeam(reviewer.getId(), status, type);
            case EMPLOYEE -> throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to review leave");
        };
        return items.stream().map(LeaveResponse::from).toList();
    }

    /**
     * Updates the status of a pending request to APPROVED or REJECTED.
     * 
     * Validation Rules:
     * - Target status must be APPROVED or REJECTED (cannot revert to PENDING).
     * - Only requests currently in PENDING state can be updated.
     */
    @Transactional
    public LeaveResponse updateStatus(String email, Long id, StatusUpdateRequest request) {
        if (request.status() == LeaveStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Status must be APPROVED or REJECTED");
        }

        // Enforce mandatory rejection reason: admin must explain why a request is denied
        if (request.status() == LeaveStatus.REJECTED) {
            String remarks = request.adminRemarks();
            if (remarks == null || remarks.trim().length() < 5) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "A rejection reason is required and must be at least 5 characters");
            }
        }

        User reviewer = currentUser(email);
        LeaveRequest leave = leaveRepo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found"));
        ensureCanReview(reviewer, leave);
        String remarks = request.adminRemarks() == null || request.adminRemarks().isBlank()
                ? null
                : request.adminRemarks().trim();
        if (leaveRepo.updatePendingStatus(id, request.status(), remarks, LeaveStatus.PENDING) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending requests can be approved or rejected");
        }

        LeaveRequest updated = leaveRepo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found"));
        notificationService.notifyLeaveDecision(updated);
        auditService.record(email, request.status() == LeaveStatus.APPROVED ? "LEAVE_APPROVED" : "LEAVE_REJECTED",
                "LeaveRequest", updated.getId().toString(),
                request.status() == LeaveStatus.REJECTED ? "Leave request rejected with reviewer remarks" : "Leave request approved",
                "SUCCESS");
        LeaveEmailEvent.Kind kind = request.status() == LeaveStatus.APPROVED
                ? LeaveEmailEvent.Kind.APPROVED
                : LeaveEmailEvent.Kind.REJECTED;
        LeaveEmailEvent event = emailEvent(kind, updated, remarks);
        emailService.enqueueLeaveEvent(event);
        eventPublisher.publishEvent(event);
        return LeaveResponse.from(updated);
    }

    // =========================================================================
    // Helper & Validation Methods
    // =========================================================================

    private LeaveEmailEvent emailEvent(LeaveEmailEvent.Kind kind, LeaveRequest leave, String remarks) {
        User applicant = leave.getUser();
        User manager = applicant.getManager();
        return new LeaveEmailEvent(kind, leave.getId(), applicant.getName(), applicant.getEmail(),
                manager == null ? null : manager.getEmail(),
                leave.getLeaveType(), leave.getFromDate(), leave.getToDate(), remarks);
    }

    /**
     * Finds the user entity corresponding to the provided email from the security token.
     */
    private User currentUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    /**
     * Finds a leave request owned by the current user that is currently PENDING.
     * 
     * ID Enumeration Defense:
     * If a request belongs to someone else, this method returns 404 NOT_FOUND instead of 403 FORBIDDEN.
     * This prevents attackers from guessing valid leave request IDs of other users.
     */
    private LeaveRequest findOwnPending(String email, Long id, String action) {
        User user = currentUser(email);
        LeaveRequest leave = leaveRepo.findByIdAndUserIdForUpdate(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending requests can be " + action);
        }
        return leave;
    }

    private void ensureCanReview(User reviewer, LeaveRequest leave) {
        User applicant = leave.getUser();
        if (applicant.getId().equals(reviewer.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot review your own leave request");
        }
        boolean allowed = switch (reviewer.getRole()) {
            case ADMIN, HR -> true;
            case MANAGER -> applicant.getManager() != null
                    && applicant.getManager().getId().equals(reviewer.getId());
            case EMPLOYEE -> false;
        };
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only review leave requests from your direct reports");
        }
    }

    /**
     * Enforces logical date ordering and reasonable enterprise bounds.
     */
    private void validateDates(LeaveRequestDto dto) {
        if (dto.toDate().isBefore(dto.fromDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date cannot be earlier than start date");
        }
        if (dto.fromDate().isBefore(java.time.LocalDate.now().minusDays(30))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Leave start date cannot be older than 30 days in the past");
        }
        if (dto.fromDate().isAfter(java.time.LocalDate.now().plusYears(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Leave start date cannot be more than 1 year in advance");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(dto.fromDate(), dto.toDate()) >= 60) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A single leave application cannot exceed 60 consecutive days");
        }
    }
}
