package com.settribe.leave.dto;

import com.settribe.leave.entity.EmploymentType;
import com.settribe.leave.entity.Role;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Partial update: null fields are left unchanged.
 * managerId of 0 or less removes the manager.
 */
public record AdminUserUpdateRequest(
        @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters") String name,
        Role role,
        @Size(max = 100) String department,
        @Size(max = 100) String designation,
        @Pattern(regexp = "^[+\\d\\s()\\-]{0,25}$", message = "Phone number must be a valid format") String phoneNumber,
        Long managerId,
        LocalDate joiningDate,
        EmploymentType employmentType,
        Boolean active
) {}
