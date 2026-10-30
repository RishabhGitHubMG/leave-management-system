package com.settribe.leave.service;

import com.settribe.leave.dto.NotificationPreferenceDto;
import com.settribe.leave.dto.NotificationResponse;
import com.settribe.leave.dto.UnreadCountResponse;
import com.settribe.leave.entity.LeaveRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.Notification;
import com.settribe.leave.entity.NotificationPreference;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.exception.ApiException;
import com.settribe.leave.repository.NotificationPreferenceRepository;
import com.settribe.leave.repository.NotificationRepository;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final NotificationPreferenceRepository preferenceRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listMine(String email, Pageable pageable) {
        User user = currentUser(email);
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(NotificationResponse::from);
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
    public NotificationPreferenceDto preferences(String email) {
        User user = currentUser(email);
        return NotificationPreferenceDto.from(preferenceRepo.findByUserId(user.getId())
                .orElseGet(() -> preferenceRepo.save(defaultPreferences(user))));
    }

    @Transactional
    public NotificationPreferenceDto updatePreferences(String email, NotificationPreferenceDto request) {
        User user = currentUser(email);
        if (request.slackEnabled() || request.teamsEnabled() || request.whatsappEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "External notification channels are not enabled");
        }
        NotificationPreference preference = preferenceRepo.findByUserId(user.getId())
                .orElseGet(() -> defaultPreferences(user));
        preference.setInAppEnabled(request.inAppEnabled());
        preference.setEmailEnabled(request.emailEnabled());
        preference.setDailyDigestEnabled(request.dailyDigestEnabled());
        preference.setApprovalRemindersEnabled(request.approvalRemindersEnabled());
        preference.setSlackEnabled(request.slackEnabled());
        preference.setTeamsEnabled(request.teamsEnabled());
        preference.setWhatsappEnabled(request.whatsappEnabled());
        return NotificationPreferenceDto.from(preferenceRepo.save(preference));
    }

    @Transactional(readOnly = true)
    public boolean remindersEnabled(User user) {
        return preferenceRepo.findByUserId(user.getId())
                .map(NotificationPreference::isApprovalRemindersEnabled).orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean digestEnabled(User user) {
        return preferenceRepo.findByUserId(user.getId())
                .map(NotificationPreference::isDailyDigestEnabled).orElse(true);
    }

    @Transactional
    public void notifyLeaveSubmission(LeaveRequest leave) {
        User applicant = leave.getUser();
        String path = "/dashboard?requestId=" + leave.getId();
        User manager = applicant.getManager();
        if (manager != null) {
            createForUser(manager, "Leave request awaiting review", applicant.getName()
                    + " submitted " + leave.getLeaveType().name().toLowerCase() + " leave from "
                    + leave.getFromDate() + " to " + leave.getToDate() + ".", path,
                    "leave:" + leave.getId() + ":submitted:manager:" + manager.getId());
        }
        userRepo.findByRoleInAndActiveTrue(java.util.List.of(Role.HR, Role.ADMIN)).stream()
                .filter(user -> manager == null || !user.getId().equals(manager.getId()))
                .forEach(user -> createForUser(user, "Leave request submitted", applicant.getName()
                        + " submitted " + leave.getLeaveType().name().toLowerCase() + " leave from "
                        + leave.getFromDate() + " to " + leave.getToDate() + ".", path,
                        "leave:" + leave.getId() + ":submitted:reviewer:" + user.getId()));
    }

    @Transactional
    public void notifyLeaveDecision(LeaveRequest leave) {
        if (leave.getStatus() != LeaveStatus.APPROVED && leave.getStatus() != LeaveStatus.REJECTED) {
            return;
        }
        String outcome = leave.getStatus() == LeaveStatus.APPROVED ? "approved" : "rejected";
        String title = leave.getStatus() == LeaveStatus.APPROVED ? "Leave request approved" : "Leave request rejected";
        String message = String.format("Your %s leave from %s to %s was %s.",
                leave.getLeaveType().name().toLowerCase(), leave.getFromDate(), leave.getToDate(), outcome);
        createForUser(leave.getUser(), title, message, "/dashboard?requestId=" + leave.getId(),
                "leave:" + leave.getId() + ":" + leave.getStatus().name().toLowerCase());
    }

    private void createForUser(User user, String title, String message, String relatedPath, String eventKey) {
        if (notificationRepo.existsByEventKey(eventKey)) {
            return;
        }
        NotificationPreference preferences = preferenceRepo.findByUserId(user.getId()).orElse(null);
        if (preferences != null && !preferences.isInAppEnabled()) {
            return;
        }
        notificationRepo.save(Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .relatedPath(relatedPath)
                .eventKey(eventKey)
                .build());
    }

    private NotificationPreference defaultPreferences(User user) {
        return NotificationPreference.builder().user(user).build();
    }

    private User currentUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
