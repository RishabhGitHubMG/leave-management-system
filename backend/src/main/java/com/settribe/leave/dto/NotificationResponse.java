package com.settribe.leave.dto;

import com.settribe.leave.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        String relatedPath,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(), n.getRelatedPath(),
                n.isRead(), n.getCreatedAt());
    }
}
