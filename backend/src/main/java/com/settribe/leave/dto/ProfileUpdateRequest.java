package com.settribe.leave.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Fields an employee may change on their own profile. Organisational fields
 * (role, manager, department, employment type) are managed by admins.
 */
public record ProfileUpdateRequest(
        @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters") String name,
        @Pattern(regexp = "^[+\\d\\s()\\-]{0,25}$", message = "Phone number must be a valid format") String phoneNumber
) {}
