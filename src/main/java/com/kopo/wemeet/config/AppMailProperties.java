package com.kopo.wemeet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AppMailProperties는 애플리케이션 실행에 필요한 Spring 설정과 보안/외부 연동 옵션을 구성합니다.
 */
@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {
    // SMTP 메일 발송 사용 여부와 발신자 표기를 application.properties에서 바인딩한다.

    private boolean enabled = false;
    private boolean previewFallbackEnabled = false;
    private String fromAddress = "";
    private String fromName = "WeMeet";
    private String signupVerificationSubject = "[WeMeet] 이메일 인증코드 안내";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPreviewFallbackEnabled() {
        return previewFallbackEnabled;
    }

    public void setPreviewFallbackEnabled(boolean previewFallbackEnabled) {
        this.previewFallbackEnabled = previewFallbackEnabled;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getSignupVerificationSubject() {
        return signupVerificationSubject;
    }

    public void setSignupVerificationSubject(String signupVerificationSubject) {
        this.signupVerificationSubject = signupVerificationSubject;
    }
}
