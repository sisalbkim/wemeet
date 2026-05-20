package com.kopo.wemeet.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ViewModeSupport {
    public static final String VIEW_MODE_COOKIE = "VIEW_MODE";
    public static final String MOBILE = "mobile";
    public static final String DESKTOP = "desktop";

    public String resolve(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return MOBILE;
        }

        for (Cookie cookie : request.getCookies()) {
            if (VIEW_MODE_COOKIE.equals(cookie.getName())) {
                return normalize(cookie.getValue());
            }
        }

        return MOBILE;
    }

    public String normalize(String viewMode) {
        return DESKTOP.equalsIgnoreCase(viewMode) ? DESKTOP : MOBILE;
    }

    public void write(HttpServletResponse response, String viewMode) {
        ResponseCookie cookie = ResponseCookie.from(VIEW_MODE_COOKIE, normalize(viewMode))
                .path("/")
                .maxAge(Duration.ofDays(365))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String sanitizeRedirectTarget(String redirectTarget) {
        if (redirectTarget == null || redirectTarget.isBlank()) {
            return "/";
        }
        if (!redirectTarget.startsWith("/") || redirectTarget.startsWith("//")) {
            return "/";
        }
        return redirectTarget;
    }
}
