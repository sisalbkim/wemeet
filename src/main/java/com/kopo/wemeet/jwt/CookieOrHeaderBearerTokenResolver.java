package com.kopo.wemeet.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * CookieOrHeaderBearerTokenResolver는 JWT 인증 토큰을 쿠키와 Authorization 헤더에서 일관되게 해석합니다.
 */
public class CookieOrHeaderBearerTokenResolver implements BearerTokenResolver {
    /*
     * Access Token 추출 규칙을 커스터마이징한 Resolver다.
     *
     * 기본 Spring Resource Server는 Authorization: Bearer ... 헤더를 본다.
     * 이 프로젝트는 화면 로그인 후 HttpOnly 쿠키에 Access Token을 저장하므로,
     * 쿠키를 먼저 확인하고 없을 때만 표준 Bearer 헤더를 확인한다.
     */

    private final String cookieName;
    private final DefaultBearerTokenResolver delegate;

    public CookieOrHeaderBearerTokenResolver(String cookieName) {
        this.cookieName = cookieName;
        this.delegate = new DefaultBearerTokenResolver();

        // 토큰이 URL이나 form body로 전달되면 로그/히스토리에 남기 쉬워서 허용하지 않는다.
        this.delegate.setAllowFormEncodedBodyParameter(false);
        this.delegate.setAllowUriQueryParameter(false);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        // 우선순위: Access Token 쿠키 -> Authorization 헤더.
        String token = resolveCookieToken(request);
        if (token != null) {
            return token;
        }

        token = resolveHeaderToken(request);
        return token == null ? delegate.resolve(request) : token;
    }

    private String resolveCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookieName == null || cookieName.isBlank()) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (!cookieName.equals(cookie.getName())) {
                continue;
            }
            // 일부 클라이언트/프록시가 따옴표나 "Bearer " 접두사를 붙여도 방어적으로 정리한다.
            String value = normalize(cookie.getValue());
            return value.isBlank() ? null : value;
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String token = value.trim();
        if ((token.startsWith("\"") && token.endsWith("\""))
                || (token.startsWith("'") && token.endsWith("'"))) {
            token = token.substring(1, token.length() - 1).trim();
        }
        String bearerPrefix = "bearer ";
        if (token.regionMatches(true, 0, bearerPrefix, 0, bearerPrefix.length())) {
            token = token.substring(bearerPrefix.length()).trim();
        }
        return token;
    }

    private String resolveHeaderToken(HttpServletRequest request) {
        String value = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (value == null || value.isBlank()) {
            return null;
        }

        String token = value.trim();
        if (!token.regionMatches(true, 0, "bearer", 0, "bearer".length())) {
            return null;
        }
        if (token.length() > "bearer".length() && !Character.isWhitespace(token.charAt("bearer".length()))) {
            return null;
        }

        token = token.substring("bearer".length()).trim();
        return token.isBlank() ? null : token;
    }
}
