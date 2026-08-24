package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.JwtConfig;
import com.kopo.wemeet.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    @Test
    void accessAndRefreshTokensAreSeparatedByType() {
        JwtTokenService service = service("test-jwt-secret");

        String accessToken = service.createAccessToken("user-123");
        String refreshToken = service.createRefreshToken("user-123");

        assertEquals("user-123", service.resolveAccessTokenUserId(accessToken).orElseThrow());
        assertEquals("user-123", service.resolveRefreshTokenUserId(refreshToken).orElseThrow());
        assertTrue(service.resolveAccessTokenUserId(refreshToken).isEmpty());
        assertTrue(service.resolveRefreshTokenUserId(accessToken).isEmpty());
    }

    @Test
    void invalidTokenReturnsEmpty() {
        JwtTokenService service = service("test-jwt-secret");

        assertTrue(service.resolveAccessTokenUserId("not-a-jwt").isEmpty());
        assertTrue(service.resolveRefreshTokenUserId("not-a-jwt").isEmpty());
    }

    @Test
    void createdTokenContainsTypeRolesAndUniqueId() throws Exception {
        String secret = "test-jwt-secret";
        JwtTokenService service = service(secret);

        String firstToken = service.createAccessToken("user-123");
        String secondToken = service.createAccessToken("user-123");
        Jwt jwt = decoder(secret).decode(firstToken);

        assertNotEquals(firstToken, secondToken);
        assertEquals("access", jwt.getClaimAsString("type"));
        assertEquals(List.of("USER"), jwt.getClaimAsStringList("roles"));
        assertTrue(jwt.getId() != null && !jwt.getId().isBlank());
    }

    @Test
    void disabledJwtDoesNotCreateToken() {
        JwtTokenService service = service("");

        assertTrue(service.resolveAccessTokenUserId("anything").isEmpty());
        assertThrows(IllegalStateException.class, () -> service.createAccessToken("user-123"));
    }

    private JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("wemeet-test");
        properties.setSecret(secret);
        properties.setAccessTtlMinutes(30);
        properties.setRefreshTtlDays(14);
        return properties;
    }

    private JwtTokenService service(String secret) {
        JwtProperties properties = properties(secret);
        JwtConfig config = new JwtConfig(properties);
        return new JwtTokenService(properties, config.jwtEncoder(config.jwtSecretKey()), config.jwtDecoder(config.jwtSecretKey()));
    }

    private JwtDecoder decoder(String secret) {
        JwtProperties properties = properties(secret);
        JwtConfig config = new JwtConfig(properties);
        return config.jwtDecoder(config.jwtSecretKey());
    }
}
