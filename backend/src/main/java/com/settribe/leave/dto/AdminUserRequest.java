package com.settribe.leave.dto;

import com.settribe.leave.entity.EmploymentType;
import com.settribe.leave.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AdminUserRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String password,

        @NotNull(message = "Role is required")
        Role role,

        @Size(max = 100) String department,
        @Size(max = 100) String designation,
        @Pattern(regexp = "^[+\\d\\s()\\-]{0,25}$", message = "Phone number must be a valid format") String phoneNumber,
        @Size(max = 50) String employeeId,
        Long managerId,
        LocalDate joiningDate,
        EmploymentType employmentType
) {}
