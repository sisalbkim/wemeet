package com.kopo.wemeet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ApiWebConfig는 애플리케이션 실행에 필요한 Spring 설정과 보안/외부 연동 옵션을 구성합니다.
 */
@Configuration
public class ApiWebConfig implements WebMvcConfigurer {
    // 개발 중 React/Vite 같은 별도 프론트가 붙을 수 있도록 API 경로에만 CORS를 허용한다.

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
