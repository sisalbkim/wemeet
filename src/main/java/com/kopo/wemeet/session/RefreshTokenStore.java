package com.kopo.wemeet.session;

import com.kopo.wemeet.config.RedisIntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RefreshTokenStore는 사용자 세션 또는 토큰 상태를 저장하고 조회하는 저장소 역할을 담당합니다.
 */
@Component
public class RefreshTokenStore {
    // Refresh Token의 jti만 보관해서 재사용을 막고 로그아웃 시 폐기할 수 있게 한다.

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);
    private static final String REFRESH_PREFIX = "wemeet:refresh:";

    private final Map<String, StoredRefreshToken> tokens = new ConcurrentHashMap<>();
    private final RedisIntegrationProperties redisProperties;
    private final StringRedisTemplate redisTemplate;

    public RefreshTokenStore(
            RedisIntegrationProperties redisProperties,
            @Nullable StringRedisTemplate redisTemplate
    ) {
        this.redisProperties = redisProperties;
        this.redisTemplate = redisTemplate;
    }

    public void store(String tokenId, String userId, Instant expiresAt) {
        if (tokenId == null || tokenId.isBlank() || userId == null || userId.isBlank() || expiresAt == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        if (shouldUseRedis()) {
            try {
                redisTemplate.opsForValue().set(REFRESH_PREFIX + tokenId, userId, ttl);
                return;
            } catch (Exception exception) {
                log.warn("Redis refresh token store unavailable, falling back to in-memory store", exception);
            }
        }
        tokens.put(tokenId, new StoredRefreshToken(userId, expiresAt));
    }

    public boolean isValid(String tokenId, String userId) {
        if (tokenId == null || tokenId.isBlank() || userId == null || userId.isBlank()) {
            return false;
        }
        if (shouldUseRedis()) {
            try {
                String storedUserId = redisTemplate.opsForValue().get(REFRESH_PREFIX + tokenId);
                return userId.equals(storedUserId);
            } catch (Exception exception) {
                log.warn("Redis refresh token lookup unavailable, using in-memory fallback", exception);
            }
        }
        StoredRefreshToken token = tokens.get(tokenId);
        if (token == null || !token.userId().equals(userId)) {
            return false;
        }
        if (token.expiresAt().isBefore(Instant.now())) {
            tokens.remove(tokenId);
            return false;
        }
        return true;
    }

    public void revoke(String tokenId) {
        if (tokenId != null && !tokenId.isBlank()) {
            if (shouldUseRedis()) {
                try {
                    redisTemplate.unlink(REFRESH_PREFIX + tokenId);
                } catch (Exception exception) {
                    log.warn("Redis refresh token revoke unavailable, using in-memory fallback", exception);
                }
            }
            tokens.remove(tokenId);
        }
    }

    private boolean shouldUseRedis() {
        return redisProperties.isEnabled() && redisTemplate != null;
    }

    private record StoredRefreshToken(String userId, Instant expiresAt) {
    }
}
