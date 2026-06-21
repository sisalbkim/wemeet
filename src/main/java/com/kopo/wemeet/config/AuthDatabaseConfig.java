package com.kopo.wemeet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AuthDatabaseConfig는 애플리케이션 실행에 필요한 Spring 설정과 보안/외부 연동 옵션을 구성합니다.
 */
@Configuration
public class AuthDatabaseConfig {
    // 인증 관련 공통 빈을 등록하는 설정 클래스다.

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 비밀번호는 평문 저장 대신 BCrypt 해시로 보관한다.
        return new BCryptPasswordEncoder();
    }
}
