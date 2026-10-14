package com.payflow.gateway;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {

    // Rate-limit bucket per client IP. Redis holds the token buckets, so the limit is shared by every gateway instance.
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(a -> a.getHostAddress())
                .orElse("unknown"));
    }
}
