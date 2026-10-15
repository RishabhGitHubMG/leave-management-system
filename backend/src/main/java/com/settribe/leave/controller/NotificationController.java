package com.settribe.leave.controller;

import com.settribe.leave.dto.NotificationResponse;
import com.settribe.leave.dto.UnreadCountResponse;
import com.settribe.leave.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints:
 * - GET /api/notifications                : Recent notifications for the authenticated user.
 * - GET /api/notifications/unread-count   : Number of unread notifications.
 * - PUT /api/notifications/{id}/read      : Mark one notification as read.
 * - PUT /api/notifications/read-all       : Mark all notifications as read.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(Authentication auth) {
        return notificationService.listMine(auth.getName());
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Authentication auth) {
        return notificationService.unreadCount(auth.getName());
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(Authentication auth, @PathVariable Long id) {
        return notificationService.markRead(auth.getName(), id);
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication auth) {
        notificationService.markAllRead(auth.getName());
        return ResponseEntity.noContent().build();
    }
}
