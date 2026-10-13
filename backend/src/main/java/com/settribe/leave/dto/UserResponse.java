package com.settribe.leave.dto;

import com.settribe.leave.entity.EmploymentType;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;

import java.time.LocalDate;

public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        String department,
        String designation,
        String phoneNumber,
        String employeeId,
        Long managerId,
        String managerName,
        LocalDate joiningDate,
        EmploymentType employmentType,
        boolean active
) {
    public static UserResponse from(User u) {
        User m = u.getManager();
        return new UserResponse(
                u.getId(),
                u.getName(),
                u.getEmail(),
                u.getRole(),
                u.getDepartment(),
                u.getDesignation(),
                u.getPhoneNumber(),
                u.getEmployeeId(),
                m == null ? null : m.getId(),
                m == null ? null : m.getName(),
                u.getJoiningDate(),
                u.getEmploymentType(),
                u.isActive());
    }
}
