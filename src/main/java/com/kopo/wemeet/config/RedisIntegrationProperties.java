package com.kopo.wemeet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RedisIntegrationProperties는 애플리케이션 실행에 필요한 Spring 설정과 보안/외부 연동 옵션을 구성합니다.
 */
@ConfigurationProperties(prefix = "app.redis")
public class RedisIntegrationProperties {
    // 세션/추천 캐시가 Redis를 사용할지와 TTL 값을 한곳에서 관리한다.

    private boolean enabled = true;
    private long sessionTtlMinutes = 720;
    private long recommendationTtlMinutes = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getSessionTtlMinutes() {
        return sessionTtlMinutes;
    }

    public void setSessionTtlMinutes(long sessionTtlMinutes) {
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    public long getRecommendationTtlMinutes() {
        return recommendationTtlMinutes;
    }

    public void setRecommendationTtlMinutes(long recommendationTtlMinutes) {
        this.recommendationTtlMinutes = recommendationTtlMinutes;
    }
}
