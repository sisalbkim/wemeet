package com.kopo.wemeet.repository;

import com.kopo.wemeet.config.RedisIntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisBackedSessionTokenStore implements SessionTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RedisBackedSessionTokenStore.class);
    private static final String SESSION_PREFIX = "wemeet:session:";

    private final Map<String, String> fallbackSessions = new ConcurrentHashMap<>();
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
                redisTemplate.opsForValue().set(
                        SESSION_PREFIX + token,
                        userId,
                        Duration.ofMinutes(redisProperties.getSessionTtlMinutes())
                );
                return;
            } catch (RuntimeException exception) {
                log.warn("Redis session store unavailable, falling back to in-memory store", exception);
            }
        }
        fallbackSessions.put(token, userId);
    }

    @Override
    public Optional<String> findUserId(String token) {
        if (shouldUseRedis()) {
            try {
                String userId = redisTemplate.opsForValue().get(SESSION_PREFIX + token);
                if (userId != null && !userId.isBlank()) {
                    return Optional.of(userId);
                }
            } catch (RuntimeException exception) {
                log.warn("Redis session lookup unavailable, using in-memory fallback", exception);
            }
        }
        return Optional.ofNullable(fallbackSessions.get(token));
    }

    private boolean shouldUseRedis() {
        return redisProperties.isEnabled() && redisTemplate != null;
    }
}
