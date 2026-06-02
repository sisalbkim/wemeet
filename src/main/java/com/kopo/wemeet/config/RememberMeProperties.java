package com.kopo.wemeet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.remember-me")
public class RememberMeProperties {
    // 자동로그인 쿠키 이름, 서명 비밀키, 유지 기간을 application.properties에서 바인딩한다.

    private String cookieName = "WM_REMEMBER_ME";
    private String secret = "";
    private int ttlDays = 14;
    private boolean cookieSecure = false;

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getTtlDays() {
        return ttlDays;
    }

    public void setTtlDays(int ttlDays) {
        this.ttlDays = ttlDays;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }
}
