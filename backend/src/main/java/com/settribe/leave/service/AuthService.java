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

/**
 * Service managing user authentication, registration, and JWT token issuance.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

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
     * 
     * @param request Validated RegisterRequest payload.
     * @return AuthResponse containing newly issued JWT token and user profile.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        // Check if an account already exists with this email address
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        // Build and persist the new User entity
        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password())) // BCrypt hashing
                .role(Role.EMPLOYEE)   // Default role: never taken from request to prevent self-assigned ADMIN
                .build();
        user = userRepository.save(user);

        return toResponse(user);
    }

    /**
     * Authenticates an existing user and issues a fresh JWT token.
     * 
     * @param request Validated LoginRequest payload.
     * @return AuthResponse with JWT token and user details upon successful login.
     * @throws ApiException with 401 UNAUTHORIZED if email or password do not match.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        // Find user by normalized email and verify raw password against BCrypt hash
        User user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        return toResponse(user);
    }

    /**
     * Converts a persisted User entity into an AuthResponse with a signed JWT token.
     */
    private AuthResponse toResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}

