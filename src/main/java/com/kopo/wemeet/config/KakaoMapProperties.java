package com.kopo.wemeet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kakao-map")
public class KakaoMapProperties {
    // 카카오 지도 JavaScript SDK 로딩에 필요한 앱 키 설정값이다.

    private String javascriptKey = "";

    public String getJavascriptKey() {
        return javascriptKey;
    }

    public void setJavascriptKey(String javascriptKey) {
        this.javascriptKey = javascriptKey;
    }

    public boolean isEnabled() {
        return javascriptKey != null && !javascriptKey.isBlank();
    }
}
