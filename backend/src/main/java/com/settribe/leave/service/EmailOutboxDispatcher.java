package com.settribe.leave.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOutboxDispatcher {

    private final EmailOutboxService outboxService;
    private final JavaMailSender mailSender;
    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatchAfterCommit(LeaveEmailEvent ignored) {
        dispatchDue();
    }

    @Scheduled(fixedDelayString = "${app.notifications.email-dispatch-ms:30000}")
    public void dispatchOnSchedule() {
        dispatchDue();
    }

    public void dispatchDue() {
        if (!emailService.isConfigured()) {
            return;
        }
        for (var item : outboxService.claimDue()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(emailService.fromAddress());
                message.setTo(item.getRecipient());
                message.setSubject(item.getSubject());
                message.setText(item.getBody());
                mailSender.send(message);
                outboxService.delivered(item.getId());
            } catch (RuntimeException failure) {
                outboxService.failed(item.getId(), item.getAttempts(), failure.getClass().getSimpleName());
                log.warn("Email outbox delivery failed for event {} ({})", item.getId(),
                        failure.getClass().getSimpleName());
            }
        }
    }
}
