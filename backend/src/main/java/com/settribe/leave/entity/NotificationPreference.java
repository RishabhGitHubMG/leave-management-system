package com.settribe.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "in_app_enabled", nullable = false)
    @Builder.Default
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = true;

    @Column(name = "daily_digest_enabled", nullable = false)
    @Builder.Default
    private boolean dailyDigestEnabled = true;

    @Column(name = "approval_reminders_enabled", nullable = false)
    @Builder.Default
    private boolean approvalRemindersEnabled = true;

    @Column(name = "slack_enabled", nullable = false)
    @Builder.Default
    private boolean slackEnabled = false;

    @Column(name = "teams_enabled", nullable = false)
    @Builder.Default
    private boolean teamsEnabled = false;

    @Column(name = "whatsapp_enabled", nullable = false)
    @Builder.Default
    private boolean whatsappEnabled = false;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
