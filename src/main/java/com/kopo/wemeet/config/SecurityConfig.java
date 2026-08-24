package com.kopo.wemeet.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig는 애플리케이션 실행에 필요한 Spring 설정과 보안/외부 연동 옵션을 구성합니다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    /*
     * Spring Security 필터 체인 설정이다.
     *
     * 기존 프로젝트는 컨트롤러와 서비스에서 직접 로그인/권한 확인을 하고 있었기 때문에,
     * 여기서는 Spring Security 기본 로그인 폼을 켜지 않는다.
     *
     * 핵심 목적:
     * - 서버 세션 기반 인증을 만들지 않는다.
     * - Resource Server JWT 필터를 등록해서 쿠키/헤더의 Access Token을 검증할 수 있게 한다.
     * - 기존 화면 URL 흐름(/login, /profile 등)은 그대로 유지한다.
     */

    private final BearerTokenResolver bearerTokenResolver;
    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final JwtRefreshFilter jwtRefreshFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // JWT 쿠키 로그인은 서버 HttpSession에 인증 상태를 저장하지 않는다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                /*
                 * CSRF는 별도 JwtCsrfFilter에서 API 변경 요청에 double-submit 방식으로 처리한다.
                 * Spring Security 기본 CSRF는 세션 기반 토큰 저장소를 전제로 하므로 여기서는 끈다.
                 */
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)

                // Spring Security 기본 로그인/Basic/Logout을 사용하면 기존 컨트롤러 흐름과 충돌하므로 비활성화한다.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                /*
                 * 공개 화면/API만 열고, 나머지는 Resource Server JWT 인증을 요구한다.
                 * 컨트롤러의 requireUser 검증은 비즈니스 레벨의 사용자 조회/검증으로 유지한다.
                 */
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(
                                "/", "/error",
                                "/css/**", "/js/**", "/img/**", "/favicon.ico",
                                "/login", "/signup", "/find-id", "/find-password", "/find-password/**",
                                "/logout",
                                "/guest/**", "/search/results", "/meetings/**",
                                "/home", "/profile/**", "/friends/**", "/history/**",
                                "/view-mode"
                        ).permitAll()
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/user-id/available",
                                "/api/auth/email/**",
                                "/api/public/**",
                                "/api/categories",
                                "/api/place-tags"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // 요청의 Access Token은 CookieOrHeaderBearerTokenResolver가 쿠키 우선으로 찾아준다.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                )
                .addFilterBefore(jwtRefreshFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}
