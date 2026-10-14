package com.payflow.order.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

// Concept: multiple SecurityFilterChains. A browser page cannot easily send a JWT header, so the server-rendered
// admin UI gets its OWN chain (matched first, @Order(1)) using HTTP Basic, while /api/** keeps the JWT chain.
@Configuration
public class AdminUiSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain adminUiChain(HttpSecurity http, AdminUiProperties props, PasswordEncoder encoder) throws Exception {
        var admin = User.withUsername(props.username()).password(encoder.encode(props.password())).roles("ADMIN").build();
        http.securityMatcher("/admin-ui/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(new InMemoryUserDetailsManager(admin));
        return http.build();
    }
}
