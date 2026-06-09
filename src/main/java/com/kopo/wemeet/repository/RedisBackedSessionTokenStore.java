package com.kopo.wemeet.repository;

import com.kopo.wemeet.config.RedisIntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisBackedSessionTokenStore implements SessionTokenStore {
    // 세션 토큰 저장소 구현체다.
    // 가능하면 Redis를 사용하고, 장애나 미설정 상황에서는 메모리로 fallback 한다.

    private static final Logger log = LoggerFactory.getLogger(RedisBackedSessionTokenStore.class);
    private static final String SESSION_PREFIX = "wemeet:session:";

    private final Map<String, FallbackSession> fallbackSessions = new ConcurrentHashMap<>();
    private final RedisIntegrationProperties redisProperties;
    private final StringRedisTemplate redisTemplate;

    public RedisBackedSessionTokenStore(
            RedisIntegrationProperties redisProperties,
            @Nullable StringRedisTemplate redisTemplate
    ) {
        this.redisProperties = redisProperties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void store(String token, String userId) {
        if (shouldUseRedis()) {
            try {
                // 세션 TTL을 적용해 오랫동안 사용하지 않은 토큰은 자연스럽게 만료되게 한다.
                redisTemplate.opsForValue().set(
                        SESSION_PREFIX + token,
                        userId,
                        sessionTtl()
                );
                return;
            } catch (RuntimeException exception) {
                log.warn("Redis session store unavailable, falling back to in-memory store", exception);
            }
        }
        fallbackSessions.put(token, new FallbackSession(userId, fallbackExpiresAt()));
    }

    @Override
    public Optional<String> findUserId(String token) {
        if (shouldUseRedis()) {
            try {
                // Redis 조회 실패 시에도 로그인 기능이 완전히 멈추지 않도록 fallback 조회를 이어간다.
                String userId = redisTemplate.opsForValue().get(SESSION_PREFIX + token);
                if (userId != null && !userId.isBlank()) {
                    redisTemplate.opsForValue().set(SESSION_PREFIX + token, userId, sessionTtl());
                    return Optional.of(userId);
                }
            } catch (RuntimeException exception) {
                log.warn("Redis session lookup unavailable, using in-memory fallback", exception);
            }
        }
        FallbackSession fallbackSession = fallbackSessions.get(token);
        if (fallbackSession == null) {
            return Optional.empty();
        }
        if (fallbackSession.expiresAt().isBefore(Instant.now())) {
            fallbackSessions.remove(token);
            return Optional.empty();
        }
        fallbackSessions.put(token, fallbackSession.refresh(fallbackExpiresAt()));
        return Optional.of(fallbackSession.userId());
    }

    private boolean shouldUseRedis() {
        // Redis 관련 빈이 없을 수 있으므로 null 여부도 함께 확인한다.
        return redisProperties.isEnabled() && redisTemplate != null;
    }

    private Duration sessionTtl() {
        return Duration.ofMinutes(Math.max(redisProperties.getSessionTtlMinutes(), 1));
    }

    private Instant fallbackExpiresAt() {
        return Instant.now().plus(sessionTtl());
    }

    private record FallbackSession(String userId, Instant expiresAt) {
        private FallbackSession refresh(Instant expiresAt) {
            return new FallbackSession(userId, expiresAt);
        }
    }
}
