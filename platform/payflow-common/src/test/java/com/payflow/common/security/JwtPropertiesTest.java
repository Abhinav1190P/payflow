package com.payflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void fillsDevDefaults() {
        var props = new JwtProperties(null, null, null);
        assertThat(props.issuer()).isEqualTo("payflow");
        assertThat(props.ttl()).isEqualTo(Duration.ofMinutes(60));
        assertThat(props.secret()).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "payflow", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
