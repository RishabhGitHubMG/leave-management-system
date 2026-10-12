package com.settribe.leave.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object (DTO) for login requests.
 * 
 * @param email    User's registered email address. Must be a valid format.
 * @param password Raw password entered by user for credential verification.
 */
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {}

