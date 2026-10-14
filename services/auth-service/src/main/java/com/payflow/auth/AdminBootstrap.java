package com.payflow.auth;

import com.payflow.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Concept: profiles. This runner only exists in local/dev/docker profiles, so a production (prod/k8s) start never
// creates a default admin account.
@Component
@Profile({"default", "dev", "docker"})
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthService authService;
    private final UserRepository users;
    private final String email;
    private final String password;

    public AdminBootstrap(AuthService authService, UserRepository users,
                          @Value("${payflow.bootstrap.admin-email}") String email,
                          @Value("${payflow.bootstrap.admin-password}") String password) {
        this.authService = authService;
        this.users = users;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByEmailIgnoreCase(email)) {
            authService.createUser(email, password, Role.ADMIN);
            log.info("created dev admin {}", email);
        }
    }
}
