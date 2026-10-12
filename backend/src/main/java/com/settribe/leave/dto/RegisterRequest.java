package com.settribe.leave.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for new employee account registration.
 * 
 * @param name     Employee's full name (letters, spaces, hyphens, apostrophes; 2-100 characters).
 * @param email    Unique email address (must include valid domain and TLD).
 * @param password Raw password (enforces 8 to 72 character length for BCrypt compatibility).
 */
public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Pattern(regexp = "^[a-zA-Z\\u00C0-\\u017F\\s'-]{2,100}$", message = "Name must contain only letters, spaces, hyphens, or apostrophes (2-100 characters)")
        String name,

        @NotBlank(message = "Email is required")
        @Email(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "Email must be a valid email address with a domain (e.g. user@company.com)")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String password
) {}


