package com.settribe.leave.dto;

import com.settribe.leave.entity.NotificationPreference;
import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceDto(
        @NotNull Boolean inAppEnabled,
        @NotNull Boolean emailEnabled,
        @NotNull Boolean dailyDigestEnabled,
        @NotNull Boolean approvalRemindersEnabled,
        @NotNull Boolean slackEnabled,
        @NotNull Boolean teamsEnabled,
        @NotNull Boolean whatsappEnabled) {

    public static NotificationPreferenceDto from(NotificationPreference preference) {
        return new NotificationPreferenceDto(
                preference.isInAppEnabled(), preference.isEmailEnabled(), preference.isDailyDigestEnabled(),
                preference.isApprovalRemindersEnabled(), preference.isSlackEnabled(), preference.isTeamsEnabled(),
                preference.isWhatsappEnabled());
    }
}
