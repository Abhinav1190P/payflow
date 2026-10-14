package com.payflow.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payflow.auth.dto.RegisterRequest;
import com.payflow.auth.dto.TokenResponse;
import com.payflow.auth.dto.UserResponse;
import com.payflow.common.security.JwtService;
import com.payflow.common.security.CommonSecurityConfig;
import com.payflow.common.security.Role;
import com.payflow.common.security.SecurityPathsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.payflow.common.ds.SlidingWindowRateLimiter;
import com.payflow.common.web.CorrelationIdFilter;
import com.payflow.common.web.RequestContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// Concept: @WebMvcTest. Loads only the web layer (controller, advice, filters, security) with the service
// mocked by Mockito, so it tests HTTP mapping, validation and error JSON in milliseconds, without a database.
@WebMvcTest(AuthController.class)
@org.springframework.test.context.ActiveProfiles("test")
@Import({CommonSecurityConfig.class, RequestContext.class})
@EnableConfigurationProperties(SecurityPathsProperties.class)
class AuthControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "loginRateLimiter")
    private SlidingWindowRateLimiter loginRateLimiter;

    @BeforeEach
    void allowRequests() {
        when(loginRateLimiter.tryAcquire(anyString())).thenReturn(true);
    }

    @Test
    void registerReturns201WithToken() throws Exception {
        var user = new UserResponse(UUID.randomUUID(), "new@payflow.dev", Role.USER);
        when(authService.register(any(RegisterRequest.class))).thenReturn(new TokenResponse("jwt-token", "Bearer", 3600, user));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@payflow.dev\",\"password\":\"Password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.user.role").value("USER"));
    }

    @Test
    void invalidBodyReturnsConsistentErrorJson() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void rateLimitedLoginReturns429() throws Exception {
        when(loginRateLimiter.tryAcquire(anyString())).thenReturn(false);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@payflow.dev\",\"password\":\"whatever1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void meWithoutTokenIs401Json() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
