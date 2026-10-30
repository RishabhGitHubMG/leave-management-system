package com.settribe.leave.service;

import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class ApprovalReminderJob {

    private final LeaveRequestRepository leaveRepo;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AtomicBoolean running = new AtomicBoolean();

    @Value("${app.notifications.approval-reminder-hours:24}")
    private int reminderIntervalHours;

    @Value("${app.timezone:UTC}")
    private String timezone;

    @Scheduled(cron = "${app.notifications.approval-reminder-cron:0 0 9 * * MON-FRI}",
            zone = "${app.timezone:UTC}")
    public void sendReminders() {
        if (reminderIntervalHours < 1 || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
            var pending = leaveRepo.findPendingOlderThan(LeaveStatus.PENDING, now.minusHours(reminderIntervalHours));
            for (var leave : pending) {
                var manager = leave.getUser().getManager();
                if (manager == null || !manager.isActive() || !notificationService.remindersEnabled(manager)) {
                    continue;
                }
                long interval = Duration.between(leave.getCreatedAt(), now).toHours() / reminderIntervalHours;
                String eventKey = "approval-reminder:" + leave.getId() + ":" + manager.getId() + ":" + interval;
                String subject = "Pending leave approval reminder";
                String body = leave.getUser().getName() + " has a "
                        + leave.getLeaveType().name().toLowerCase() + " leave request from "
                        + leave.getFromDate() + " to " + leave.getToDate() + " awaiting your review."
                        + " It has been waiting for " + Duration.between(leave.getCreatedAt(), now).toDays() + " day(s).";
                emailService.enqueueMessage(manager.getEmail(), eventKey, subject, body);
            }
        } finally {
            running.set(false);
        }
    }
}
