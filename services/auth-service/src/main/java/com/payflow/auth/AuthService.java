package com.payflow.auth;

import com.payflow.common.security.Role;
import com.payflow.auth.dto.LoginRequest;
import com.payflow.auth.dto.RegisterRequest;
import com.payflow.auth.dto.TokenResponse;
import com.payflow.auth.dto.UserResponse;
import com.payflow.common.security.JwtService;
import com.payflow.common.error.UnauthorizedException;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// Not @Transactional on purpose: password hashing happens here, outside any transaction, and only the
// short database write in AccountRegistrar holds a connection.
@Service
public class AuthService {

    private final UserRepository users;
    private final AccountRegistrar registrar;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(UserRepository users, AccountRegistrar registrar, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.registrar = registrar;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    public TokenResponse register(RegisterRequest request) {
        return token(createUser(request.email(), request.password(), Role.USER));
    }

    public User createUser(String rawEmail, String rawPassword, Role role) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        String hash = passwordEncoder.encode(rawPassword);
        return registrar.create(email, hash, role);
    }

    public TokenResponse login(LoginRequest request) {
        var user = users.findByEmailIgnoreCase(request.email().trim());
        // Run BCrypt even when the email does not exist, so response time does not reveal which emails are registered.
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new UnauthorizedException("Email or password is wrong");
        }
        return token(user.get());
    }

    private TokenResponse token(User user) {
        return new TokenResponse(jwtService.issue(user.getId(), user.getEmail(), user.getRole()), "Bearer", jwtService.ttlSeconds(), UserResponse.from(user));
    }
}
