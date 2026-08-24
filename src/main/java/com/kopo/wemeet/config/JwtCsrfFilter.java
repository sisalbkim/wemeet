package com.kopo.wemeet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JwtCsrfFilter는 애플리케이션 실행에 필요한 Spring 설정과 보안/외부 연동 옵션을 구성합니다.
 */
@Component
@RequiredArgsConstructor
public class JwtCsrfFilter extends OncePerRequestFilter {
    // JWT 쿠키가 자동 전송되는 API 변경 요청에만 double-submit CSRF 검증을 적용한다.

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final JwtProperties jwtProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isApiRequest(request)
                || SAFE_METHODS.contains(request.getMethod())
                || !hasJwtCookie(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String csrfCookie = findCookieValue(request, jwtProperties.getCsrfCookieName());
        String csrfHeader = request.getHeader(jwtProperties.getCsrfHeaderName());

        if (csrfCookie == null || csrfCookie.isBlank() || csrfHeader == null || !csrfCookie.equals(csrfHeader.trim())) {
            writeForbidden(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            requestUri = requestUri.substring(contextPath.length());
        }
        return requestUri.startsWith("/api/");
    }

    private boolean hasJwtCookie(HttpServletRequest request) {
        return findCookieValue(request, jwtProperties.getAccessCookieName()) != null
                || findCookieValue(request, jwtProperties.getRefreshCookieName()) != null;
    }

    private String findCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookieName == null || cookieName.isBlank()) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Invalid CSRF token\"}");
    }
}
