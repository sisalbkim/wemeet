package com.kopo.wemeet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NaverMapProperties는 애플리케이션 실행에 필요한 Spring 설정과 보안/외부 연동 옵션을 구성합니다.
 */
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
