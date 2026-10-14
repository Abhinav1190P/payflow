package com.payflow.auth;


import com.payflow.common.ds.SlidingWindowRateLimiter;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    // Concept: Singleton via Spring. Spring creates exactly one instance of this bean and injects it
    // everywhere it is needed, so the rate-limit state is shared, without a hand-written static singleton.
    @Bean
    public SlidingWindowRateLimiter loginRateLimiter(RateLimitProperties properties, Clock clock) {
        return new SlidingWindowRateLimiter(properties.loginMaxRequests(), properties.loginWindow(), clock);
    }
}
