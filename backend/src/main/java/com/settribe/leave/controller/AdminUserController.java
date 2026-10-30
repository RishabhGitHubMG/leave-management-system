package com.settribe.leave.controller;

import com.settribe.leave.dto.AdminUserRequest;
import com.settribe.leave.dto.AdminUserUpdateRequest;
import com.settribe.leave.dto.UserResponse;
import com.settribe.leave.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only user management. Accounts are deactivated, never deleted, so leave history stays intact.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> list() {
        return userService.listAll();
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(Authentication auth, @Valid @RequestBody AdminUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(auth.getName(), request));
    }

    @PutMapping("/{id}")
    public UserResponse update(Authentication auth,
                               @PathVariable Long id,
                               @Valid @RequestBody AdminUserUpdateRequest request) {
        return userService.update(auth.getName(), id, request);
    }
}
