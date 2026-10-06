package com.settribe.leave.dto;

import com.settribe.leave.entity.Role;

public record AuthResponse(String token, Long id, String name, String email, Role role) {}
