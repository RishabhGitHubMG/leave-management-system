package com.settribe.leave.service;

import com.settribe.leave.dto.NotificationResponse;
import com.settribe.leave.dto.UnreadCountResponse;
import com.settribe.leave.entity.LeaveRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.Notification;
import com.settribe.leave.entity.User;
import com.settribe.leave.exception.ApiException;
import com.settribe.leave.repository.NotificationRepository;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(String email) {
        User user = currentUser(email);
        return notificationRepo.findTop50ByUserIdOrderByIdDesc(user.getId())
                .stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String email) {
        User user = currentUser(email);
        return new UnreadCountResponse(notificationRepo.countByUserIdAndReadFalse(user.getId()));
    }

    @Transactional
    public NotificationResponse markRead(String email, Long id) {
        User user = currentUser(email);
        Notification notification = notificationRepo.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(String email) {
        User user = currentUser(email);
        notificationRepo.markAllReadForUser(user.getId());
    }

    @Transactional
    public void notifyLeaveDecision(LeaveRequest leave) {
        if (leave.getStatus() != LeaveStatus.APPROVED && leave.getStatus() != LeaveStatus.REJECTED) {
            return;
        }
        String outcome = leave.getStatus() == LeaveStatus.APPROVED ? "approved" : "rejected";
        String message = String.format("Your %s leave from %s to %s was %s.",
                leave.getLeaveType().name().toLowerCase(), leave.getFromDate(), leave.getToDate(), outcome);
        notificationRepo.save(Notification.builder()
                .user(leave.getUser())
                .message(message)
                .build());
    }

    private User currentUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
