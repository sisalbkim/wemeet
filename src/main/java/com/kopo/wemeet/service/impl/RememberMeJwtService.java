package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.RememberMeProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RememberMeJwtService {
    // 자동로그인용 JWT를 만들고 검증하며, 쿠키에 넣고 지우는 역할만 맡는다.

    private static final String TOKEN_TYPE = "remember-me";

    private final RememberMeProperties rememberMeProperties;

    public boolean isEnabled() {
        return !rememberMeProperties.getSecret().isBlank();
    }

    public boolean hasRememberMeCookie(HttpServletRequest request) {
        return findRememberMeCookie(request) != null;
    }

    public Optional<String> resolveUserIdFromCookie(HttpServletRequest request) {
        Cookie cookie = findRememberMeCookie(request);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return Optional.empty();
        }
        return parseUserId(cookie.getValue());
    }

    public void writeRememberMeCookie(HttpServletResponse response, String userId) {
        if (!isEnabled() || userId == null || userId.isBlank()) {
            return;
        }

        Cookie cookie = new Cookie(rememberMeProperties.getCookieName(), createToken(userId));
        cookie.setHttpOnly(true);
        cookie.setSecure(rememberMeProperties.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(Math.max(rememberMeProperties.getTtlDays(), 1) * 24 * 60 * 60);
        response.addCookie(cookie);
    }

    public void clearRememberMeCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(rememberMeProperties.getCookieName(), "");
        cookie.setHttpOnly(true);
        cookie.setSecure(rememberMeProperties.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private Cookie findRememberMeCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (rememberMeProperties.getCookieName().equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    private String createToken(String userId) {
        try {
            Instant issuedAt = Instant.now();
            Instant expiresAt = issuedAt.plusSeconds((long) Math.max(rememberMeProperties.getTtlDays(), 1) * 24 * 60 * 60);

            return Jwts.builder()
                    .subject(userId)
                    .claim("typ", TOKEN_TYPE)
                    .issuedAt(Date.from(issuedAt))
                    .expiration(Date.from(expiresAt))
                    .signWith(signingKey())
                    .compact();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create remember-me JWT", exception);
        }
    }

    private Optional<String> parseUserId(String token) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TOKEN_TYPE.equals(claims.get("typ", String.class))) {
                return Optional.empty();
            }

            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private SecretKey signingKey() {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(rememberMeProperties.getSecret().getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to create remember-me JWT signing key", exception);
        }
    }
}
