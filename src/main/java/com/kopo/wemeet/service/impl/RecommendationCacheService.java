package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.RedisIntegrationProperties;
import com.kopo.wemeet.dto.ApiDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecommendationCacheService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationCacheService.class);
    private static final String CACHE_PREFIX = "wemeet:recommendation:";

    private final ConcurrentHashMap<String, CachedRecommendation> fallbackCache = new ConcurrentHashMap<>();
    private final RedisIntegrationProperties redisProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public RecommendationCacheService(
            RedisIntegrationProperties redisProperties,
            ObjectMapper objectMapper,
            @Nullable StringRedisTemplate redisTemplate
    ) {
        this.redisProperties = redisProperties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public Optional<ApiDtos.RecommendationResponse> get(String cacheKey) {
        if (shouldUseRedis()) {
            try {
                String payload = redisTemplate.opsForValue().get(CACHE_PREFIX + cacheKey);
                if (payload != null && !payload.isBlank()) {
                    return Optional.of(objectMapper.readValue(payload, ApiDtos.RecommendationResponse.class));
                }
            } catch (Exception exception) {
                log.warn("Redis recommendation cache lookup failed, using fallback cache", exception);
            }
        }

        CachedRecommendation cached = fallbackCache.get(cacheKey);
        if (cached == null || cached.expiresAt().isBefore(Instant.now())) {
            fallbackCache.remove(cacheKey);
            return Optional.empty();
        }
        return Optional.of(cached.response());
    }

    public void put(String cacheKey, ApiDtos.RecommendationResponse response) {
        if (shouldUseRedis()) {
            try {
                redisTemplate.opsForValue().set(
                        CACHE_PREFIX + cacheKey,
                        objectMapper.writeValueAsString(response),
                        Duration.ofMinutes(redisProperties.getRecommendationTtlMinutes())
                );
                return;
            } catch (Exception exception) {
                log.warn("Redis recommendation cache write failed, using fallback cache", exception);
            }
        }

        fallbackCache.put(
                cacheKey,
                new CachedRecommendation(
                        response,
                        Instant.now().plus(Duration.ofMinutes(redisProperties.getRecommendationTtlMinutes()))
                )
        );
    }

    private boolean shouldUseRedis() {
        return redisProperties.isEnabled() && redisTemplate != null;
    }

    private record CachedRecommendation(
            ApiDtos.RecommendationResponse response,
            Instant expiresAt
    ) {
    }
}
