package com.payflow.common.web;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

// Concept: bean scopes. Most beans are singletons (one instance for the app). This one is
// request-scoped: Spring creates a fresh instance per HTTP request and injects a proxy into
// singletons, so each request sees its own correlation id and client IP.
@Component
@RequestScope
public class RequestContext {

    private String correlationId;
    private String clientIp;

    public String correlationId() {
        return correlationId;
    }

    public String clientIp() {
        return clientIp;
    }

    public void populate(String correlationId, String clientIp) {
        this.correlationId = correlationId;
        this.clientIp = clientIp;
    }
}
