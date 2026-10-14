package com.payflow.auth;

import com.payflow.auth.dto.LoginRequest;
import com.payflow.auth.dto.RegisterRequest;
import com.payflow.auth.dto.TokenResponse;
import com.payflow.auth.dto.UserResponse;
import com.payflow.common.security.AuthenticatedUser;
import com.payflow.common.api.ApiResponse;
import com.payflow.common.ds.SlidingWindowRateLimiter;
import com.payflow.common.error.TooManyRequestsException;
import com.payflow.common.web.RequestContext;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SlidingWindowRateLimiter loginRateLimiter;
    private final RequestContext requestContext;

    public AuthController(AuthService authService, @Qualifier("loginRateLimiter") SlidingWindowRateLimiter loginRateLimiter,
                          RequestContext requestContext) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
        this.requestContext = requestContext;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        throttle();
        return ApiResponse.of(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        throttle();
        return ApiResponse.of(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.of(new UserResponse(user.id(), user.email(), user.role()));
    }

    private void throttle() {
        if (!loginRateLimiter.tryAcquire("auth:" + requestContext.clientIp())) {
            throw new TooManyRequestsException();
        }
    }
}
