package com.settribe.leave.service;

import com.settribe.leave.dto.AdminUserRequest;
import com.settribe.leave.dto.AdminUserUpdateRequest;
import com.settribe.leave.dto.ProfileUpdateRequest;
import com.settribe.leave.dto.UserResponse;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.exception.ApiException;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_REPORTING_DEPTH = 100;

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepo.findAll(Sort.by("name")).stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse me(String email) {
        return UserResponse.from(currentUser(email));
    }

    @Transactional
    public UserResponse updateProfile(String email, ProfileUpdateRequest request) {
        User user = currentUser(email);
        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber().trim());
        }
        auditService.record(email, "USER_PROFILE_UPDATED", "User", user.getId().toString(),
                "User profile fields updated", "SUCCESS");
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse create(String adminEmail, AdminUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepo.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .department(trimOrNull(request.department()))
                .designation(trimOrNull(request.designation()))
                .phoneNumber(trimOrNull(request.phoneNumber()))
                .employeeId(trimOrNull(request.employeeId()))
                .joiningDate(request.joiningDate())
                .employmentType(request.employmentType())
                .build();
        user.setManager(resolveManager(null, request.managerId()));
        User saved = userRepo.save(user);
        auditService.record(adminEmail, "USER_CREATED", "User", saved.getId().toString(),
                "User account created with role " + saved.getRole(), "SUCCESS");
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(String adminEmail, Long id, AdminUserUpdateRequest request) {
        User actor = currentUser(adminEmail);
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        boolean self = actor.getId().equals(user.getId());
        Role previousRole = user.getRole();
        boolean previousActive = user.isActive();

        if (request.role() != null) {
            if (self && request.role() != Role.ADMIN) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot remove your own admin role");
            }
            if (request.role() == Role.EMPLOYEE && userRepo.existsByManager_Id(user.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "Reassign this user's direct reports before changing their role");
            }
            user.setRole(request.role());
        }
        if (request.active() != null) {
            if (self && !request.active()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account");
            }
            if (!request.active() && userRepo.existsByManager_Id(user.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "Reassign this user's direct reports before deactivating them");
            }
            user.setActive(request.active());
        }
        if (request.managerId() != null) {
            user.setManager(resolveManager(user, request.managerId()));
        }
        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.department() != null) {
            user.setDepartment(trimOrNull(request.department()));
        }
        if (request.designation() != null) {
            user.setDesignation(trimOrNull(request.designation()));
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(trimOrNull(request.phoneNumber()));
        }
        if (request.joiningDate() != null) {
            user.setJoiningDate(request.joiningDate());
        }
        if (request.employmentType() != null) {
            user.setEmploymentType(request.employmentType());
        }
        if (previousRole != user.getRole()) {
            auditService.record(adminEmail, "ROLE_CHANGED", "User", user.getId().toString(),
                    "Role changed from " + previousRole + " to " + user.getRole(), "SUCCESS");
        }
        if (previousActive != user.isActive()) {
            auditService.record(adminEmail, "USER_ACCOUNT_STATUS_CHANGED", "User", user.getId().toString(),
                    user.isActive() ? "User account activated" : "User account deactivated", "SUCCESS");
        }
        auditService.record(adminEmail, "USER_UPDATED", "User", user.getId().toString(),
                "Administrative user fields updated", "SUCCESS");
        return UserResponse.from(user);
    }

    /**
     * Resolves the manager for a user. A non-positive id clears the manager (returns null).
     * Rejects self-management and any assignment that would create a reporting loop.
     */
    private User resolveManager(User subject, Long managerId) {
        if (managerId == null || managerId <= 0) {
            return null;
        }
        User manager = userRepo.findById(managerId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Manager not found"));
        if (!manager.isActive() || manager.getRole() == Role.EMPLOYEE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Manager must be an active reviewer");
        }
        if (subject == null) {
            return manager;
        }
        if (manager.getId().equals(subject.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A user cannot be their own manager");
        }
        User cursor = manager;
        for (int depth = 0; cursor != null && depth < MAX_REPORTING_DEPTH; depth++) {
            if (cursor.getId().equals(subject.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "This assignment would create a reporting loop");
            }
            cursor = cursor.getManager();
        }
        return manager;
    }

    private User currentUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private static String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
