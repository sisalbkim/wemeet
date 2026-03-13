package com.kopo.wemeet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.redis")
public class RedisIntegrationProperties {

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
