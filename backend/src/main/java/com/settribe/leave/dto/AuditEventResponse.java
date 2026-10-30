package com.settribe.leave.dto;

import com.settribe.leave.entity.AuditEvent;

import java.time.LocalDateTime;

public record AuditEventResponse(
        Long id,
        Long actorId,
        String actorName,
        String actorEmail,
        String action,
        String entityType,
        String entityId,
        String changeSummary,
        String requestId,
        String clientIp,
        String outcome,
        LocalDateTime createdAt) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getActor() == null ? null : event.getActor().getId(),
                event.getActor() == null ? null : event.getActor().getName(),
                event.getActor() == null ? null : event.getActor().getEmail(),
                event.getAction(), event.getEntityType(), event.getEntityId(), event.getChangeSummary(),
                event.getRequestId(), event.getClientIp(), event.getOutcome(), event.getCreatedAt());
    }
}
