package com.payflow.auth.dto;

import com.payflow.common.security.Role;

import com.payflow.auth.User;
import java.util.UUID;

public record UserResponse(UUID id, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
