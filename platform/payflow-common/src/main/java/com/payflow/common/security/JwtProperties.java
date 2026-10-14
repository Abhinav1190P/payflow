package com.payflow.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Concept: @ConfigurationProperties with a compact constructor supplying dev defaults.
// In docker/k8s the secret always comes from the PAYFLOW_JWT_SECRET environment variable via config-server.
@ConfigurationProperties(prefix = "payflow.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    static final String DEV_SECRET = "dev-only-secret-change-me-dev-only-secret-change-me";

    public JwtProperties {
        secret = secret == null || secret.isBlank() ? DEV_SECRET : secret;
        issuer = issuer == null || issuer.isBlank() ? "payflow" : issuer;
        ttl = ttl == null ? Duration.ofMinutes(60) : ttl;
        if (secret.length() < 32) {
            throw new IllegalArgumentException("payflow.jwt.secret must be at least 32 characters");
        }
    }
}
