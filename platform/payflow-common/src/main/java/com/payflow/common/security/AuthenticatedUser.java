package com.payflow.common.security;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, Role role) {
}
