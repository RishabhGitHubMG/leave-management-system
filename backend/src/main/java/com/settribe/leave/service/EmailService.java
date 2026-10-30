package com.settribe.leave.service;

import com.settribe.leave.entity.EmailOutbox;
import com.settribe.leave.entity.NotificationPreference;
import com.settribe.leave.repository.EmailOutboxRepository;
import com.settribe.leave.repository.NotificationPreferenceRepository;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailOutboxRepository outboxRepo;
    private final UserRepository userRepo;
    private final NotificationPreferenceRepository preferenceRepo;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${spring.mail.password:}")
    private String password;

    @Value("${app.hr-email:}")
    private String hrEmail;

    public boolean isConfigured() {
        return !fromAddress.isBlank() && !password.isBlank();
    }

    public String fromAddress() {
        return fromAddress;
    }

    @Transactional
    public void enqueueLeaveEvent(LeaveEmailEvent event) {
        if (fromAddress.isBlank() || password.isBlank()) {
            return;
        }
        Map<String, String> recipients = new LinkedHashMap<>();
        addIfEnabled(recipients, event.employeeEmail());
        addIfEnabled(recipients, event.managerEmail());
        addIfEnabled(recipients, hrEmail);
        String subject = subject(event);
        String body = body(event);
        recipients.forEach((normalizedAddress, address) -> {
            String eventKey = "leave:" + event.requestId() + ":" + event.kind().name() + ":" + normalizedAddress;
            if (!outboxRepo.existsByEventKey(eventKey)) {
                outboxRepo.save(EmailOutbox.builder()
                        .eventKey(eventKey)
                        .recipient(address)
                        .subject(subject)
                        .body(body)
                        .availableAt(LocalDateTime.now())
                        .build());
            }
        });
    }

    private void addIfEnabled(Map<String, String> recipients, String address) {
        if (address == null || address.isBlank()) {
            return;
        }
        String normalized = address.trim().toLowerCase(Locale.ROOT);
        var user = userRepo.findByEmail(normalized).orElse(null);
        NotificationPreference preferences = user == null ? null
                : preferenceRepo.findByUserId(user.getId()).orElse(null);
        if (preferences == null || preferences.isEmailEnabled()) {
            recipients.putIfAbsent(normalized, address.trim());
        }
    }

    private static String subject(LeaveEmailEvent event) {
        String title = switch (event.kind()) {
            case SUBMITTED -> "Leave request submitted";
            case APPROVED -> "Leave request approved";
            case REJECTED -> "Leave request rejected";
            case CANCELLED -> "Leave request cancelled";
        };
        return title + " - " + event.employeeName();
    }

    private static String body(LeaveEmailEvent event) {
        String leave = event.leaveType().name().toLowerCase(Locale.ROOT);
        String details = event.employeeName() + "'s " + leave + " leave from " + event.fromDate()
                + " to " + event.toDate();
        return switch (event.kind()) {
            case SUBMITTED -> details + " was submitted and is awaiting review.";
            case APPROVED -> details + " was approved.";
            case REJECTED -> details + " was rejected.\n\nReviewer remarks: " + event.remarks();
            case CANCELLED -> details + " was cancelled.";
        };
    }
}
