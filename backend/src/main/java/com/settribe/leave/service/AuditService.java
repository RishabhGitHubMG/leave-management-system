package com.settribe.leave.service;

import com.settribe.leave.dto.AuditEventResponse;
import com.settribe.leave.entity.AuditEvent;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.AuditEventRepository;
import com.settribe.leave.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditRepo;
    private final UserRepository userRepo;

    @Transactional
    public void record(String actorEmail, String action, String entityType, String entityId,
                       String summary, String outcome) {
        User actor = actorEmail == null ? null : userRepo.findByEmail(actorEmail).orElse(null);
        HttpServletRequest request = currentRequest();
        String requestId = request == null ? null : request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
            requestId = request == null ? null : UUID.randomUUID().toString();
        }
        auditRepo.save(AuditEvent.builder()
                .actor(actor)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .changeSummary(summary)
                .requestId(requestId)
                .clientIp(request == null ? null : request.getRemoteAddr())
                .outcome(outcome)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> search(String actorEmail, String action, String entityType,
                                           LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return auditRepo.search(blankToNull(actorEmail), blankToNull(action), blankToNull(entityType), from, to,
                pageable).map(AuditEventResponse::from);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
