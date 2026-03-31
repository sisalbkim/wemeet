package com.kopo.wemeet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.naver-map")
public class NaverMapProperties {
    // 네이버 지도 JavaScript SDK 로딩에 필요한 Key ID 설정값이다.

    private String keyId = "";

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public boolean isEnabled() {
        return keyId != null && !keyId.isBlank();
    }
}
