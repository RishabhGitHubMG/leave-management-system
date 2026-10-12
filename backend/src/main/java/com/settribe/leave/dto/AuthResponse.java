package com.settribe.leave.dto;

import com.settribe.leave.entity.Role;

/**
 * Data Transfer Object (DTO) returned upon successful registration or login.
 *
 * @param token       Signed JWT Bearer token used for authenticating subsequent API requests.
 * @param id          Unique ID of the authenticated user.
 * @param name        Full name of the user.
 * @param email       Email address of the user.
 * @param role        User role (EMPLOYEE or ADMIN).
 * @param department  The department the user belongs to.
 * @param designation The user's job title or designation.
 * @param phoneNumber The user's contact phone number.
 * @param employeeId  The user's unique employee identifier (e.g., "SET-1234").
 */
public record AuthResponse(
        String token,
        Long id,
        String name,
        String email,
        Role role,
        String department,
        String designation,
        String phoneNumber,
        String employeeId
) {}
