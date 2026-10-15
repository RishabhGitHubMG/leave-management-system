package com.settribe.leave.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Fields an employee may change on their own profile. Organisational fields
 * (role, manager, department, employment type) are managed by admins.
 */
public record ProfileUpdateRequest(
        @Pattern(regexp = "^(?=.*\\S)[a-zA-Z\\u00C0-\\u017F\\s'-]{2,100}$",
                message = "Name must contain only letters, spaces, hyphens, or apostrophes (2-100 characters)")
        String name,
        @Pattern(regexp = "^[+\\d\\s()\\-]{0,25}$", message = "Phone number must be a valid format") String phoneNumber
) {}
