package com.payflow.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.catalog.dto.ProductResponse;
import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
public class RedisCacheConfig {

    // Store products as readable JSON (not Java serialization), typed to ProductResponse, with a 10 minute TTL.
    @Bean
    public RedisCacheManagerBuilderCustomizer productCacheCustomizer(ObjectMapper objectMapper) {
        var serializer = new Jackson2JsonRedisSerializer<>(objectMapper, ProductResponse.class);
        return builder -> builder.withCacheConfiguration(ProductService.PRODUCT_CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10))
                        .disableCachingNullValues()
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer)));
    }
}
