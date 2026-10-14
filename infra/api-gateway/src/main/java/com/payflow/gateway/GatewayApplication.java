package com.payflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Concept: API gateway. The single public entry point: routes /api/** to the right service (found through
// Eureka, load-balanced with "lb://"), applies a Redis-backed rate limit per client IP, adds CORS and a
// correlation id. Services themselves are not exposed directly.
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
