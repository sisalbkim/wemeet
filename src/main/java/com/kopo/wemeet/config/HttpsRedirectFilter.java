package com.kopo.wemeet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class HttpsRedirectFilter extends OncePerRequestFilter {
    // 운영 환경에서 HTTP 요청을 HTTPS로 강제 전환할 때 사용하는 필터다.

    private final boolean requireHttps;

    public HttpsRedirectFilter(@Value("${app.security.require-https:false}") boolean requireHttps) {
        this.requireHttps = requireHttps;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // HTTPS 강제가 꺼져 있거나 이미 HTTPS로 들어온 요청이면 그대로 통과시킨다.
        if (!requireHttps) {
            return true;
        }
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 현재 요청 URL 정보를 유지한 채 프로토콜만 https로 바꿔 리다이렉트한다.
        StringBuilder redirectUrl = new StringBuilder("https://")
                .append(request.getServerName());

        boolean defaultHttpPort = request.getServerPort() == 80 || request.getServerPort() == 443;
        if (!defaultHttpPort) {
            redirectUrl.append(":").append(request.getServerPort());
        }

        redirectUrl.append(request.getRequestURI());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            redirectUrl.append("?").append(request.getQueryString());
        }

        response.sendRedirect(redirectUrl.toString());
    }
}
