package com.settribe.leave.service;

import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.Role;
import com.settribe.leave.repository.LeaveRequestRepository;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class ManagerDigestJob {

    private final LeaveRequestRepository leaveRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AtomicBoolean running = new AtomicBoolean();

    @Value("${app.timezone:UTC}")
    private String timezone;

    @Value("${app.base-url:}")
    private String baseUrl;

    @Scheduled(cron = "${app.notifications.manager-digest-cron:0 0 8 * * MON-FRI}",
            zone = "${app.timezone:UTC}")
    public void sendDigests() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            LocalDate today = LocalDate.now(ZoneId.of(timezone));
            for (var manager : userRepo.findByRoleAndActiveTrue(Role.MANAGER)) {
                if (!notificationService.digestEnabled(manager)) {
                    continue;
                }
                var pending = leaveRepo.findForTeam(manager.getId(), LeaveStatus.PENDING, null);
                var approved = leaveRepo.findForTeamInDateRange(manager.getId(), LeaveStatus.APPROVED,
                        today, today.plusDays(14));
                String report = buildReport(manager.getName(), today, pending, approved);
                String key = "manager-digest:" + manager.getId() + ":" + today;
                emailService.enqueueMessage(manager.getEmail(), key,
                        "Team leave digest for " + today, report);
            }
        } finally {
            running.set(false);
        }
    }

    private String buildReport(String managerName, LocalDate today, java.util.List<com.settribe.leave.entity.LeaveRequest> pending,
                               java.util.List<com.settribe.leave.entity.LeaveRequest> approved) {
        String appUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        StringBuilder body = new StringBuilder("Team leave digest for ").append(today)
                .append("\nHello ").append(managerName).append(",\n");
        body.append("\nPending approvals (").append(pending.size()).append("):\n");
        if (pending.isEmpty()) {
            body.append("None.\n");
        } else {
            for (var leave : pending) {
                long waitingDays = Math.max(0, java.time.Duration.between(leave.getCreatedAt(), today.atStartOfDay()).toDays());
                body.append("- ").append(leave.getUser().getName()).append(": ")
                        .append(leave.getFromDate()).append(" to ").append(leave.getToDate())
                        .append(" (").append(waitingDays).append(" day(s) waiting)\n");
            }
        }
        body.append("\nApproved leave today / upcoming:\n");
        if (approved.isEmpty()) {
            body.append("None in the next 14 days.\n");
        } else {
            for (var leave : approved) {
                body.append("- ").append(leave.getUser().getName()).append(": ")
                        .append(leave.getFromDate()).append(" to ").append(leave.getToDate())
                        .append(leave.getFromDate().isBefore(today) && !leave.getToDate().isBefore(today)
                                ? " (absent today)" : "").append("\n");
            }
        }
        body.append("\nReview requests: ").append(appUrl).append("/dashboard?tab=all");
        return body.toString();
    }
}
