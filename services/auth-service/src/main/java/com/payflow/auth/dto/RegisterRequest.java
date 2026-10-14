package com.payflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Concept: records as DTOs. Immutable, with constructor, accessors, equals, hashCode and toString generated.
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***]";
    }
}
