package com.kopo.wemeet.config;

import com.kopo.wemeet.jwt.CookieOrHeaderBearerTokenResolver;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * JwtConfig는 애플리케이션 실행에 필요한 Spring 설정과 보안/외부 연동 옵션을 구성합니다.
 */
@Configuration
@RequiredArgsConstructor
public class JwtConfig {
    /*
     * Spring Security JWT 구성 클래스다.
     *
     * 이 프로젝트는 기존 jjwt(Jwts.builder/parser)를 직접 호출하지 않고,
     * Spring Security가 제공하는 JwtEncoder/JwtDecoder를 사용한다.
     *
     * 전체 흐름:
     * 1. jwtSecretKey(): application.properties의 app.auth.jwt.secret 값을 HS256 키로 변환
     * 2. jwtEncoder(): 로그인 성공 시 Access/Refresh JWT를 서명해서 발급
     * 3. jwtDecoder(): 요청으로 들어온 JWT의 서명/만료/형식을 검증
     * 4. bearerTokenResolver(): Access Token을 Authorization 헤더보다 쿠키에서 먼저 찾음
     */

    private final JwtProperties jwtProperties;

    @Bean
    public SecretKey jwtSecretKey() {
        try {
            /*
             * HS256은 256비트 이상의 키가 필요하다.
             * 사용자가 짧은 secret을 설정해도 일정한 32바이트 키가 되도록 SHA-256으로 해시한다.
             * 운영 환경에서는 JWT_SECRET을 충분히 긴 랜덤 문자열로 지정하는 것이 좋다.
             */
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "HmacSHA256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to create JWT signing key", exception);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        // NimbusJwtEncoder는 JWKSource를 통해 서명 키를 받는다. HS256 대칭키는 ImmutableSecret으로 감싼다.
        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(jwtSecretKey);
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        // alg가 HS256인 토큰만 허용한다. 다른 알고리즘으로 바뀐 토큰은 검증 단계에서 거부된다.
        return NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        /*
         * JWT roles 클레임을 Spring Security 권한으로 변환한다.
         * 예: roles=["USER"] -> ROLE_USER
         * 현재 컨트롤러는 기존 서비스 메서드로 사용자를 확인하지만,
         * oauth2ResourceServer 필터에서도 동일한 권한 해석이 가능하게 맞춰 둔다.
         */
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        // 교수님 예제처럼 Access Token 쿠키를 우선 사용하고, 없을 때 Authorization 헤더를 사용한다.
        return new CookieOrHeaderBearerTokenResolver(jwtProperties.getAccessCookieName());
    }
}
