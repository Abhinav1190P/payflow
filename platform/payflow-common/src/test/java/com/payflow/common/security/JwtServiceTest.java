package com.payflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payflow.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-unit-test";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-05-01T12:00:00Z"));

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private JwtService service(String secret, String issuer) {
        return new JwtService(new JwtProperties(secret, issuer, Duration.ofMinutes(15)), clock);
    }

    private static String issue(JwtService jwt, Role role) {
        return jwt.issue(USER_ID, "dev@payflow.dev", role);
    }

    @Test
    void roundTripsUserClaims() throws Exception {
        JwtService jwt = service(SECRET, "payflow");
        AuthenticatedUser parsed = jwt.parse(issue(jwt, Role.ADMIN));
        assertThat(parsed).isEqualTo(new AuthenticatedUser(USER_ID, "dev@payflow.dev", Role.ADMIN));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt = service(SECRET, "payflow");
        String token = issue(jwt, Role.USER);
        clock.advance(Duration.ofMinutes(16));
        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsTamperedOrForeignTokens() {
        JwtService jwt = service(SECRET, "payflow");
        String token = issue(jwt, Role.USER);
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(InvalidTokenException.class);

        String otherKey = issue(service("another-secret-another-secret-another-secret", "payflow"), Role.USER);
        assertThatThrownBy(() -> jwt.parse(otherKey)).isInstanceOf(InvalidTokenException.class);

        String otherIssuer = issue(service(SECRET, "someone-else"), Role.USER);
        assertThatThrownBy(() -> jwt.parse(otherIssuer)).isInstanceOf(InvalidTokenException.class);

        assertThatThrownBy(() -> jwt.parse("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
    }
}
