package com.kopo.wemeet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

@Component
public class HttpsRedirectFilter extends OncePerRequestFilter {
    // 운영 환경에서 HTTP 요청을 HTTPS로 강제 전환할 때 사용하는 필터다.

    private final boolean requireHttps;
    private final String canonicalHost;

    public HttpsRedirectFilter(
            @Value("${app.security.require-https:false}") boolean requireHttps,
            @Value("${app.security.canonical-host:}") String canonicalHost
    ) {
        this.requireHttps = requireHttps;
        this.canonicalHost = normalizeHost(canonicalHost);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (isLocalDevelopmentHost(resolveRequestHost(request))) {
            return true;
        }
        return !requiresRedirect(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        response.sendRedirect(buildRedirectUrl(request));
    }

    private boolean requiresRedirect(HttpServletRequest request) {
        return requiresHttpsRedirect(request) || requiresCanonicalHostRedirect(request);
    }

    private boolean requiresHttpsRedirect(HttpServletRequest request) {
        return requireHttps && !isHttpsRequest(request);
    }

    private boolean requiresCanonicalHostRedirect(HttpServletRequest request) {
        if (canonicalHost.isBlank()) {
            return false;
        }
        return !canonicalHost.equals(normalizeHost(resolveRequestHost(request)));
    }

    private boolean isHttpsRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        return "https".equalsIgnoreCase(firstHeaderValue(request.getHeader("X-Forwarded-Proto")));
    }

    private String buildRedirectUrl(HttpServletRequest request) {
        String scheme = requireHttps ? "https" : request.getScheme();
        String targetHost = canonicalHost.isBlank() ? normalizeHost(resolveRequestHost(request)) : canonicalHost;
        int targetPort = resolveRequestPort(request);

        StringBuilder redirectUrl = new StringBuilder(scheme)
                .append("://")
                .append(targetHost);

        if (!isDefaultPort(scheme, targetPort)) {
            redirectUrl.append(":").append(targetPort);
        }

        redirectUrl.append(request.getRequestURI());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            redirectUrl.append("?").append(request.getQueryString());
        }
        return redirectUrl.toString();
    }

    private String resolveRequestHost(HttpServletRequest request) {
        String forwardedHost = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        if (!forwardedHost.isBlank()) {
            return forwardedHost;
        }

        String hostHeader = firstHeaderValue(request.getHeader("Host"));
        if (!hostHeader.isBlank()) {
            return hostHeader;
        }

        return request.getServerName();
    }

    private int resolveRequestPort(HttpServletRequest request) {
        String forwardedPort = firstHeaderValue(request.getHeader("X-Forwarded-Port"));
        if (!forwardedPort.isBlank()) {
            try {
                return Integer.parseInt(forwardedPort);
            } catch (NumberFormatException ignored) {
            }
        }

        Integer headerPort = extractPort(firstHeaderValue(request.getHeader("X-Forwarded-Host")));
        if (headerPort != null) {
            return headerPort;
        }

        headerPort = extractPort(firstHeaderValue(request.getHeader("Host")));
        if (headerPort != null) {
            return headerPort;
        }

        if (requireHttps || isHttpsRequest(request)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(request.getScheme())) {
            return 80;
        }

        return request.getServerPort();
    }

    private static String firstHeaderValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return "";
        }
        return headerValue.split(",", 2)[0].trim();
    }

    private static String normalizeHost(String host) {
        String trimmed = firstHeaderValue(host);
        if (trimmed.isBlank()) {
            return "";
        }
        if (trimmed.startsWith("[")) {
            int bracketEnd = trimmed.indexOf(']');
            if (bracketEnd >= 0) {
                return trimmed.substring(0, bracketEnd + 1).toLowerCase(Locale.ROOT);
            }
        }

        int colonIndex = trimmed.lastIndexOf(':');
        if (colonIndex > -1 && trimmed.indexOf(':') == colonIndex) {
            trimmed = trimmed.substring(0, colonIndex);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static Integer extractPort(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }

        if (host.startsWith("[")) {
            int separatorIndex = host.indexOf("]:");
            if (separatorIndex > -1) {
                return parsePort(host.substring(separatorIndex + 2));
            }
            return null;
        }

        int colonIndex = host.lastIndexOf(':');
        if (colonIndex > -1 && host.indexOf(':') == colonIndex) {
            return parsePort(host.substring(colonIndex + 1));
        }
        return null;
    }

    private static Integer parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private static boolean isLocalDevelopmentHost(String host) {
        String normalizedHost = normalizeHost(host);
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost)
                || "::1".equals(normalizedHost);
    }
}
