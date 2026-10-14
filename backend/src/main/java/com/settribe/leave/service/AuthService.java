package com.settribe.leave.service;

import com.settribe.leave.dto.AuthResponse;
import com.settribe.leave.dto.LoginRequest;
import com.settribe.leave.dto.RegisterRequest;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.exception.ApiException;
import com.settribe.leave.repository.UserRepository;
import com.settribe.leave.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service managing user authentication, registration, and JWT token issuance.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Registers a new user with EMPLOYEE role.
     *
     * Security Highlights:
     * - Email is normalized (trimmed and converted to lowercase).
     * - Prevents duplicate registrations by checking existsByEmail.
     * - Password is encrypted with BCrypt before persistence.
     * - The role is explicitly hardcoded to Role.EMPLOYEE to prevent privilege escalation.
     * - Employee ID is auto-generated in "SET-XXXX" format if not provided by the user.
     *
     * @param request Validated RegisterRequest payload.
     * @return AuthResponse containing newly issued JWT token and user profile.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }

        // Check if an account already exists with this email address
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        // Auto-generate employeeId in "SET-XXXX" format if user did not provide one
        String employeeId = (request.employeeId() != null && !request.employeeId().isBlank())
                ? request.employeeId().trim()
                : "SET-" + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 9999));

        // Build and persist the new User entity with extended profile fields
        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password())) // BCrypt hashing
                .role(Role.EMPLOYEE)   // Default role: never taken from request to prevent self-assigned ADMIN
                .department(request.department() != null ? request.department().trim() : null)
                .designation(request.designation() != null ? request.designation().trim() : null)
                .phoneNumber(request.phoneNumber() != null ? request.phoneNumber().trim() : null)
                .employeeId(employeeId)
                .build();
        user = userRepository.save(user);

        return toResponse(user);
    }

    /**
     * Authenticates an existing user and issues a fresh JWT token.
     *
     * Failed attempts are persisted so the counter survives the exception rollback;
     * the account locks for LOCK_DURATION after MAX_FAILED_ATTEMPTS consecutive failures.
     *
     * @param request Validated LoginRequest payload.
     * @return AuthResponse with JWT token and user details upon successful login.
     * @throws ApiException with 401 for bad credentials or deactivated accounts, 423 while locked.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        ApiException invalid = new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalid;
        }

        User user = userRepository.findByEmail(email).orElseThrow(() -> invalid);
        if (!user.isActive()) {
            throw invalid;
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.LOCKED,
                    "Account is temporarily locked after repeated failed sign-in attempts. Try again later.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCK_DURATION));
                attempts = 0;
            }
            user.setFailedLoginAttempts(attempts);
            throw invalid;
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return toResponse(user);
    }

    /**
     * Converts a persisted User entity into an AuthResponse with a signed JWT token.
     * Includes all profile fields for the frontend to display in the dashboard header.
     */
    private AuthResponse toResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return new AuthResponse(
                token,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getDepartment(),
                user.getDesignation(),
                user.getPhoneNumber(),
                user.getEmployeeId()
        );
    }
}
