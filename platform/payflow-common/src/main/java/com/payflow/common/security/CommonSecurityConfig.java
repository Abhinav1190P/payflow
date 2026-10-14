package com.payflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Concept: Spring Security, shared by every service. Stateless: no sessions, no CSRF (no cookies), every request
// authenticated by the JWT filter. @EnableMethodSecurity turns on @PreAuthorize. Public paths come from YAML.
@Configuration
@EnableMethodSecurity
public class CommonSecurityConfig {

    @Bean
    @Order(100)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper,
                                                      SecurityPathsProperties paths) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    paths.publicPaths().forEach(p -> permit(auth, p));
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health/**",
                            "/actuator/health", "/actuator/prometheus", "/actuator/info", "/error").permitAll();
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, objectMapper, HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED", "Missing or invalid access token", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, objectMapper, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "You do not have access to this resource", req.getRequestURI())))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Concept: BCrypt. A slow, salted hash; BCrypt only uses the first 72 bytes, hence the 72-char password limit.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void permit(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth, String spec) {
        String[] parts = spec.trim().split("\\s+", 2);
        if (parts.length == 2) {
            auth.requestMatchers(HttpMethod.valueOf(parts[0]), parts[1]).permitAll();
        } else {
            auth.requestMatchers(parts[0]).permitAll();
        }
    }

    private static void writeError(HttpServletResponse res, ObjectMapper mapper, HttpStatus status, String code, String message, String path)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), GlobalExceptionHandler.body(status, code, message, path));
    }
}
