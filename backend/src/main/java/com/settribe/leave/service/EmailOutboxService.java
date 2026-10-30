package com.settribe.leave.service;

import com.settribe.leave.entity.EmailDeliveryAttempt;
import com.settribe.leave.entity.EmailOutbox;
import com.settribe.leave.repository.EmailDeliveryAttemptRepository;
import com.settribe.leave.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailOutboxService {

    private final EmailOutboxRepository outboxRepo;
    private final EmailDeliveryAttemptRepository attemptRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<EmailOutbox> claimDue() {
        LocalDateTime now = LocalDateTime.now();
        outboxRepo.releaseStale(now, now.minusMinutes(10));
        List<EmailOutbox> due = outboxRepo.findTop20ByStatusInAndAvailableAtBeforeOrderByIdAsc(
                List.of("PENDING", "RETRY"), now);
        return due.stream()
                .filter(item -> outboxRepo.claim(item.getId(), now.plusMinutes(10)) == 1)
                .map(item -> outboxRepo.findById(item.getId()).orElseThrow())
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delivered(Long id) {
        EmailOutbox item = outboxRepo.findById(id).orElseThrow();
        item.setStatus("DELIVERED");
        item.setDeliveredAt(LocalDateTime.now());
        item.setLastError(null);
        attemptRepo.save(EmailDeliveryAttempt.builder()
                .outbox(item)
                .attemptNumber(item.getAttempts())
                .outcome("DELIVERED")
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Long id, int attempts, String error) {
        EmailOutbox item = outboxRepo.findById(id).orElseThrow();
        item.setLastError("Mail provider failure (" + error + ")");
        attemptRepo.save(EmailDeliveryAttempt.builder()
                .outbox(item)
                .attemptNumber(attempts)
                .outcome("FAILED")
                .errorType(error)
                .build());
        if (attempts >= 6) {
            item.setStatus("FAILED");
            return;
        }
        item.setStatus("RETRY");
        long delayMinutes = Math.min(5L * (1L << Math.min(Math.max(attempts - 1, 0), 10)), 360L);
        item.setAvailableAt(LocalDateTime.now().plusMinutes(delayMinutes));
    }
}
