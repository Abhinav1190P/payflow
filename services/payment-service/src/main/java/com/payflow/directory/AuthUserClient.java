package com.payflow.directory;

import com.payflow.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

// Concept: OpenFeign. A declarative HTTP client: Spring generates the implementation from this interface.
// "auth-service" is a Eureka service name, so the call is load-balanced across all auth-service instances.
@FeignClient(name = "auth-service", path = "/api/v1/users")
public interface AuthUserClient {

    @GetMapping("/lookup")
    ApiResponse<UserSummary> lookup(@RequestParam("email") String email,
                                    @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    record UserSummary(UUID id, String email, String role) {
    }
}
