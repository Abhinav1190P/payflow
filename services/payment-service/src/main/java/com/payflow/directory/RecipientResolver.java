package com.payflow.directory;

import com.payflow.common.error.NotFoundException;
import com.payflow.common.error.PayflowException;
import com.payflow.common.error.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.springframework.stereotype.Component;

// Concept: Resilience4j around a remote call (settings in application.yml, instance "auth-service"):
// - Retry: a failed call is retried with exponential backoff (a blip should not fail the user's transfer).
// - CircuitBreaker: if most recent calls fail, stop calling for a while and fail fast instead of piling up
//   waiting threads on a sick service.
// - RateLimiter: cap how hard this service can hit auth-service.
// "Not found" is a real answer, not a failure: it is ignored by retry and circuit breaker (see YAML).
@Component
public class RecipientResolver {

    private final AuthUserClient client;

    public RecipientResolver(AuthUserClient client) {
        this.client = client;
    }

    @Retry(name = "auth-service")
    @CircuitBreaker(name = "auth-service", fallbackMethod = "unavailable")
    @RateLimiter(name = "auth-service")
    public UUID resolve(String email, String authorization) {
        try {
            return client.lookup(email.trim(), authorization).data().id();
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Recipient");
        }
    }

    private UUID unavailable(String email, String authorization, Throwable cause) {
        if (cause instanceof PayflowException business) {
            throw business;
        }
        throw new ServiceUnavailableException("User directory is unavailable, try again shortly");
    }
}
