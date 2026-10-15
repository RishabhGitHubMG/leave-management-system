package com.settribe.leave.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.hr-email:}")
    private String hrEmail;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeaveEvent(LeaveEmailEvent event) {
        String subject = subject(event);
        String body = body(event);
        for (String recipient : recipients(event)) {
            send(recipient, subject, body);
        }
    }

    private Set<String> recipients(LeaveEmailEvent event) {
        Set<String> recipients = new LinkedHashSet<>();
        addIfPresent(recipients, event.employeeEmail());
        addIfPresent(recipients, event.managerEmail());
        addIfPresent(recipients, hrEmail);
        return recipients;
    }

    private static void addIfPresent(Set<String> recipients, String address) {
        if (address != null && !address.isBlank()) {
            recipients.add(address.trim());
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
        StringBuilder body = new StringBuilder()
                .append(event.employeeName()).append("'s ")
                .append(event.leaveType().name().toLowerCase())
                .append(" leave from ").append(event.fromDate())
                .append(" to ").append(event.toDate())
                .append(" was ").append(event.kind().name().toLowerCase()).append(".");
        if (event.remarks() != null && !event.remarks().isBlank()) {
            body.append("\n\nReviewer remarks: ").append(event.remarks());
        }
        return body.toString();
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) {
            message.setFrom(fromAddress);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Could not send leave email '{}': {}", subject, ex.getMessage());
        }
    }
}
