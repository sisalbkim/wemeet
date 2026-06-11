package com.kopo.wemeet.session;

import com.kopo.wemeet.config.RedisIntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisBackedSessionTokenStore implements SessionTokenStore {
    // 세션 토큰은 JPA 엔티티가 아니라 Redis key-value 또는 메모리 fallback으로 관리한다.

    private static final Logger log = LoggerFactory.getLogger(RedisBackedSessionTokenStore.class);
    private static final String SESSION_PREFIX = "wemeet:session:";

    private final Map<String, FallbackSession> fallbackSessions = new ConcurrentHashMap<>();
    private final RedisIntegrationProperties redisProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisBackedSessionTokenStore(
            RedisIntegrationProperties redisProperties,
            @Nullable StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisProperties = redisProperties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(String token, String userId) {
        if (shouldUseRedis()) {
            try {
                redisTemplate.opsForValue().set(
                        SESSION_PREFIX + token,
                        objectMapper.writeValueAsString(new RedisSessionValue(userId, sessionTtlMinutes())),
                        sessionTtl()
                );
                return;
            } catch (Exception exception) {
                log.warn("Redis session store unavailable, falling back to in-memory store", exception);
            }
        }
        fallbackSessions.put(token, new FallbackSession(userId, fallbackExpiresAt()));
    }

    @Override
    public Optional<String> findUserId(String token) {
        if (shouldUseRedis()) {
            try {
                String payload = redisTemplate.opsForValue().get(SESSION_PREFIX + token);
                String userId = parseUserId(payload);
                if (userId != null && !userId.isBlank()) {
                    redisTemplate.opsForValue().set(
                            SESSION_PREFIX + token,
                            objectMapper.writeValueAsString(new RedisSessionValue(userId, sessionTtlMinutes())),
                            sessionTtl()
                    );
                    return Optional.of(userId);
                }
            } catch (Exception exception) {
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
        return redisProperties.isEnabled() && redisTemplate != null;
    }

    private Duration sessionTtl() {
        return Duration.ofMinutes(sessionTtlMinutes());
    }

    private long sessionTtlMinutes() {
        return Math.max(redisProperties.getSessionTtlMinutes(), 1);
    }

    private Instant fallbackExpiresAt() {
        return Instant.now().plus(sessionTtl());
    }

    private String parseUserId(String payload) throws Exception {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        if (!payload.trim().startsWith("{")) {
            return payload;
        }
        return objectMapper.readValue(payload, RedisSessionValue.class).USER_ID();
    }

    private record RedisSessionValue(
            String USER_ID,
            long TTL_MINUTES
    ) {
    }

    private record FallbackSession(String userId, Instant expiresAt) {
        private FallbackSession refresh(Instant expiresAt) {
            return new FallbackSession(userId, expiresAt);
        }
    }
}
