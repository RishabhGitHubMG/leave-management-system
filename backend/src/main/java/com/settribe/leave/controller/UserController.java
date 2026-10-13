package com.settribe.leave.controller;

import com.settribe.leave.dto.ProfileUpdateRequest;
import com.settribe.leave.dto.UserResponse;
import com.settribe.leave.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserResponse me(Authentication auth) {
        return userService.me(auth.getName());
    }

    @PutMapping
    public UserResponse updateProfile(Authentication auth, @Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(auth.getName(), request);
    }
}
