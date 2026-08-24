package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JwtTokenService는 도메인 규칙과 외부 연동 흐름을 조합해 실제 비즈니스 처리를 수행합니다.
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {
    /*
     * API/화면 인증용 JWT 발급, 검증, 쿠키 저장을 담당한다.
     *
     * 주의:
     * - jjwt를 직접 사용하지 않는다.
     * - 발급은 Spring Security JwtEncoder가 담당한다.
     * - 검증은 Spring Security JwtDecoder가 담당한다.
     * - 실제 요청 인증은 Access Token으로 하고, Refresh Token은 재발급/로그아웃 처리에만 사용한다.
     */

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public boolean isEnabled() {
        return !jwtProperties.getSecret().isBlank();
    }

    public String createAccessToken(String userId) {
        // Access Token은 매 요청 인증에 쓰이므로 짧은 TTL을 사용한다.
        return createToken(userId, TYPE_ACCESS, Math.max(jwtProperties.getAccessTtlMinutes(), 1) * 60);
    }

    public String createRefreshToken(String userId) {
        // Refresh Token은 Access Token 재발급용이므로 Access Token보다 긴 TTL을 사용한다.
        return createToken(userId, TYPE_REFRESH, Math.max(jwtProperties.getRefreshTtlDays(), 1) * 24 * 60 * 60);
    }

    public Optional<String> resolveAccessTokenUserId(String token) {
        return resolveUserId(token, TYPE_ACCESS);
    }

    public Optional<String> resolveRefreshTokenUserId(String token) {
        return resolveUserId(token, TYPE_REFRESH);
    }

    public Optional<TokenDetails> resolveRefreshTokenDetails(String token) {
        return resolveTokenDetails(token, TYPE_REFRESH);
    }

    public void writeTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        writeTokenCookies(response, accessToken, refreshToken, true);
    }

    public void writeTokenCookies(HttpServletResponse response, String accessToken, String refreshToken, boolean persistent) {
        if (!isEnabled()) {
            return;
        }

        /*
         * autoLogin 체크 여부에 따라 쿠키 저장 방식을 바꾼다.
         *
         * persistent=true  : Max-Age를 명시해서 브라우저 종료 후에도 쿠키 유지
         * persistent=false : Max-Age=-1로 세션 쿠키 발급, 브라우저 종료 시 삭제
         *
         * 토큰 종류는 항상 Access/Refresh 두 가지다. autoLogin은 별도 토큰이 아니라 쿠키 수명 정책이다.
         */
        long accessMaxAge = persistent ? Math.max(jwtProperties.getAccessTtlMinutes(), 1) * 60 : -1;
        long refreshMaxAge = persistent ? Math.max(jwtProperties.getRefreshTtlDays(), 1) * 24 * 60 * 60 : -1;
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(
                jwtProperties.getAccessCookieName(),
                accessToken,
                accessMaxAge
        ).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(
                jwtProperties.getRefreshCookieName(),
                refreshToken,
                refreshMaxAge
        ).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(
                jwtProperties.getCsrfCookieName(),
                createCsrfToken(),
                accessMaxAge,
                false
        ).toString());
    }

    public void clearTokenCookies(HttpServletResponse response) {
        // 같은 이름/path로 Max-Age=0 쿠키를 내려 브라우저의 JWT 쿠키를 삭제한다.
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(jwtProperties.getAccessCookieName(), "", 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(jwtProperties.getRefreshCookieName(), "", 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(jwtProperties.getCsrfCookieName(), "", 0, false).toString());
    }

    public void writeRefreshedAccessCookie(HttpServletResponse response, String accessToken) {
        if (!isEnabled() || response == null || accessToken == null || accessToken.isBlank()) {
            return;
        }
        long accessMaxAge = Math.max(jwtProperties.getAccessTtlMinutes(), 1) * 60;
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(
                jwtProperties.getAccessCookieName(),
                accessToken,
                accessMaxAge
        ).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(
                jwtProperties.getCsrfCookieName(),
                createCsrfToken(),
                accessMaxAge,
                false
        ).toString());
    }

    private String createToken(String userId, String type, long ttlSeconds) {
        if (!isEnabled() || userId == null || userId.isBlank()) {
            throw new IllegalStateException("API JWT is not configured");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);

        /*
         * JWT 클레임 구성:
         * - iss: 발급자. application.properties의 app.auth.jwt.issuer와 일치해야 한다.
         * - sub: 사용자 ID. 토큰 검증 후 실제 AppUser 조회에 사용한다.
         * - type: access/refresh 구분. Access Token 자리에 Refresh Token을 쓰지 못하게 막는다.
         * - roles: Spring Security 권한 변환용 클레임.
         * - jti: Refresh Token 저장소에서 회전/폐기할 때 쓰는 토큰 고유 ID.
         */
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(userId)
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_ROLES, List.of("USER"))
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    private Optional<String> resolveUserId(String token, String expectedType) {
        return resolveTokenDetails(token, expectedType).map(TokenDetails::userId);
    }

    private Optional<TokenDetails> resolveTokenDetails(String token, String expectedType) {
        if (!isEnabled() || token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);

            // NimbusJwtDecoder가 서명/만료를 먼저 검증하고, 여기서는 프로젝트 정책 클레임을 추가 확인한다.
            if (!jwtProperties.getIssuer().equals(jwt.getClaimAsString("iss"))) {
                return Optional.empty();
            }

            // Access/Refresh 토큰의 용도를 강제로 분리한다.
            String tokenType = jwt.getClaimAsString(CLAIM_TYPE);
            if (!expectedType.equals(tokenType)) {
                return Optional.empty();
            }

            String userId = jwt.getSubject();
            String tokenId = jwt.getId();
            Instant expiration = jwt.getExpiresAt();
            if (userId == null || userId.isBlank() || tokenId == null || tokenId.isBlank() || expiration == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenDetails(userId, tokenId, expiration));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private ResponseCookie buildCookie(String name, String value, long maxAgeSeconds) {
        return buildCookie(name, value, maxAgeSeconds, true);
    }

    private ResponseCookie buildCookie(String name, String value, long maxAgeSeconds, boolean httpOnly) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(jwtProperties.isCookieSecure())
                .path("/")
                .sameSite(normalizedSameSite())
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String createCsrfToken() {
        return UUID.randomUUID().toString();
    }

    private String normalizedSameSite() {
        String sameSite = jwtProperties.getCookieSameSite();
        return sameSite == null || sameSite.isBlank() ? "Lax" : sameSite.trim();
    }

    public record TokenDetails(String userId, String tokenId, Instant expiresAt) {
    }
}
