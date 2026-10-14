package com.payflow.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Each service lists its public endpoints in YAML, e.g. "POST /api/v1/auth/login" or "GET /api/v1/products/**".
@ConfigurationProperties(prefix = "payflow.security")
public record SecurityPathsProperties(List<String> publicPaths) {

    public SecurityPathsProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
