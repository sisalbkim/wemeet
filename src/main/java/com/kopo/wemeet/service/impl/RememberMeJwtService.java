package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.RememberMeProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class RememberMeJwtService {
    // 자동로그인용 JWT를 만들고 검증하며, 쿠키에 넣고 지우는 역할만 맡는다.

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_TYPE = "remember-me";

    private final RememberMeProperties rememberMeProperties;
    private final ObjectMapper objectMapper;

    public RememberMeJwtService(RememberMeProperties rememberMeProperties, ObjectMapper objectMapper) {
        this.rememberMeProperties = rememberMeProperties;
        this.objectMapper = objectMapper;
    }

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
            long issuedAt = Instant.now().getEpochSecond();
            long expiresAt = Instant.now().plusSeconds((long) Math.max(rememberMeProperties.getTtlDays(), 1) * 24 * 60 * 60).getEpochSecond();

            Map<String, Object> header = Map.of(
                    "alg", "HS256",
                    "typ", "JWT"
            );
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", userId);
            payload.put("typ", TOKEN_TYPE);
            payload.put("iat", issuedAt);
            payload.put("exp", expiresAt);

            String encodedHeader = encode(objectMapper.writeValueAsBytes(header));
            String encodedPayload = encode(objectMapper.writeValueAsBytes(payload));
            String unsignedToken = encodedHeader + "." + encodedPayload;
            String signature = encode(sign(unsignedToken));
            return unsignedToken + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create remember-me JWT", exception);
        }
    }

    private Optional<String> parseUserId(String token) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }

        String unsignedToken = parts[0] + "." + parts[1];
        byte[] providedSignature;
        try {
            providedSignature = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        byte[] expectedSignature;
        try {
            expectedSignature = sign(unsignedToken);
        } catch (Exception exception) {
            return Optional.empty();
        }

        if (!MessageDigest.isEqual(providedSignature, expectedSignature)) {
            return Optional.empty();
        }

        try {
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!TOKEN_TYPE.equals(payload.path("typ").asText())) {
                return Optional.empty();
            }
            if (payload.path("exp").asLong(0L) <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }

            String userId = payload.path("sub").asText("");
            if (userId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private byte[] sign(String unsignedToken) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(rememberMeProperties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
