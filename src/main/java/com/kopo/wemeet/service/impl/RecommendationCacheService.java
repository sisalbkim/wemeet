package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.*;

import com.kopo.wemeet.config.RedisIntegrationProperties;
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

/**
 * RecommendationCacheService는 도메인 규칙과 외부 연동 흐름을 조합해 실제 비즈니스 처리를 수행합니다.
 */
@Service
public class RecommendationCacheService {
    // 추천 결과 캐시 전용 서비스다.
    // Redis가 가능하면 Redis를 쓰고, 아니면 메모리 캐시로 내려와서 동작한다.

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

    public Optional<RecommendationDTO.RecommendationResponse> get(String cacheKey) {
        if (shouldUseRedis()) {
            try {
                // Redis에는 정의서의 PAYLOAD_JSON 구조로 저장해 두었다가 다시 DTO로 복원한다.
                String payload = redisTemplate.opsForValue().get(CACHE_PREFIX + cacheKey);
                if (payload != null && !payload.isBlank()) {
                    return Optional.of(readRecommendation(payload));
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

    public void put(String cacheKey, RecommendationDTO.RecommendationResponse response) {
        if (shouldUseRedis()) {
            try {
                // TTL을 둬서 주소나 후보 데이터가 조금씩 바뀌더라도 캐시가 오래 고정되지 않게 한다.
                redisTemplate.opsForValue().set(
                        CACHE_PREFIX + cacheKey,
                        objectMapper.writeValueAsString(new RedisRecommendationValue(response, recommendationTtlMinutes())),
                        Duration.ofMinutes(recommendationTtlMinutes())
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
                        Instant.now().plus(Duration.ofMinutes(recommendationTtlMinutes()))
                )
        );
    }

    private boolean shouldUseRedis() {
        // Redis 설정이 켜져 있고 실제 템플릿 빈도 존재할 때만 Redis 사용.
        return redisProperties.isEnabled() && redisTemplate != null;
    }

    private RecommendationDTO.RecommendationResponse readRecommendation(String payload) throws Exception {
        try {
            RedisRecommendationValue cached = objectMapper.readValue(payload, RedisRecommendationValue.class);
            if (cached.PAYLOAD_JSON() != null) {
                return cached.PAYLOAD_JSON();
            }
        } catch (Exception ignored) {
            // 기존 캐시 값은 RecommendationResponse 자체 JSON이므로 아래에서 다시 읽는다.
        }
        return objectMapper.readValue(payload, RecommendationDTO.RecommendationResponse.class);
    }

    private long recommendationTtlMinutes() {
        return Math.max(redisProperties.getRecommendationTtlMinutes(), 1);
    }

    private record RedisRecommendationValue(
            RecommendationDTO.RecommendationResponse PAYLOAD_JSON,
            long TTL_MINUTES
    ) {
    }

    private record CachedRecommendation(
            RecommendationDTO.RecommendationResponse response,
            Instant expiresAt
    ) {
        // 메모리 fallback 캐시에서 응답 본문과 만료 시각을 함께 보관한다.
    }
}
