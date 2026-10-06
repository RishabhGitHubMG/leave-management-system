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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRepo;
    private final UserRepository userRepo;

    // ---------- Employee ----------

    @Transactional(readOnly = true)
    public List<LeaveResponse> listMine(String email, LeaveStatus status, LeaveType type) {
        User user = currentUser(email);
        return leaveRepo.findForUser(user.getId(), status, type)
                .stream().map(LeaveResponse::from).toList();
    }

    @Transactional
    public LeaveResponse create(String email, LeaveRequestDto dto) {
        validateDates(dto);
        User user = currentUser(email);

        LeaveRequest leave = LeaveRequest.builder()
                .user(user)
                .leaveType(dto.leaveType())
                .fromDate(dto.fromDate())
                .toDate(dto.toDate())
                .reason(dto.reason().trim())
                .status(LeaveStatus.PENDING)
                .build();
        return LeaveResponse.from(leaveRepo.save(leave));
    }

    @Transactional
    public LeaveResponse update(String email, Long id, LeaveRequestDto dto) {
        validateDates(dto);
        LeaveRequest leave = findOwnPending(email, id, "edited");

        leave.setLeaveType(dto.leaveType());
        leave.setFromDate(dto.fromDate());
        leave.setToDate(dto.toDate());
        leave.setReason(dto.reason().trim());
        return LeaveResponse.from(leaveRepo.save(leave));
    }

    @Transactional
    public void delete(String email, Long id) {
        LeaveRequest leave = findOwnPending(email, id, "cancelled");
        leaveRepo.delete(leave);
    }

    // ---------- Admin ----------

    @Transactional(readOnly = true)
    public List<LeaveResponse> listAll(LeaveStatus status, LeaveType type) {
        return leaveRepo.findAllFiltered(status, type)
                .stream().map(LeaveResponse::from).toList();
    }

    @Transactional
    public LeaveResponse updateStatus(Long id, StatusUpdateRequest request) {
        if (request.status() == LeaveStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Status must be APPROVED or REJECTED");
        }
        LeaveRequest leave = leaveRepo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending requests can be approved or rejected");
        }
        leave.setStatus(request.status());
        return LeaveResponse.from(leaveRepo.save(leave));
    }

    // ---------- Helpers ----------

    private User currentUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    // Someone else's request looks identical to a missing one (404), so IDs can't be probed.
    private LeaveRequest findOwnPending(String email, Long id, String action) {
        User user = currentUser(email);
        LeaveRequest leave = leaveRepo.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending requests can be " + action);
        }
        return leave;
    }

    private void validateDates(LeaveRequestDto dto) {
        if (dto.toDate().isBefore(dto.fromDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "To date must not be before from date");
        }
    }
}
