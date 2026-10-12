package com.settribe.leave.controller;

import com.settribe.leave.dto.AuthResponse;
import com.settribe.leave.dto.LoginRequest;
import com.settribe.leave.dto.RegisterRequest;
import com.settribe.leave.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller handling public authentication endpoints.
 * 
 * Endpoints:
 * - POST /api/auth/register : Registers a new employee account and returns a JWT token.
 * - POST /api/auth/login    : Authenticates user credentials and returns a JWT token.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Registers a new employee user account.
     * 
     * @param request Validated registration payload containing name, email, and password.
     * @return HTTP 201 Created with AuthResponse containing JWT token and user info.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * Authenticates an existing user (Employee or Admin).
     * 
     * @param request Validated login payload containing email and password.
     * @return HTTP 200 OK with AuthResponse containing JWT token and user info.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}

