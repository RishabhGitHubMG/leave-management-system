package com.settribe.leave.controller;

import com.settribe.leave.dto.NotificationPreferenceDto;
import com.settribe.leave.dto.NotificationResponse;
import com.settribe.leave.dto.UnreadCountResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.settribe.leave.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


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
    public Page<NotificationResponse> list(Authentication auth,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification pagination");
        }
        return notificationService.listMine(auth.getName(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/preferences")
    public NotificationPreferenceDto preferences(Authentication auth) {
        return notificationService.preferences(auth.getName());
    }

    @PutMapping("/preferences")
    public NotificationPreferenceDto updatePreferences(Authentication auth,
                                                       @Valid @RequestBody NotificationPreferenceDto request) {
        return notificationService.updatePreferences(auth.getName(), request);
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
