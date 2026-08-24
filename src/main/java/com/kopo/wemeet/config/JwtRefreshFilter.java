package com.kopo.wemeet.config;

import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.service.impl.JwtTokenService;
import com.kopo.wemeet.session.RefreshTokenStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * JwtRefreshFilter는 만료된 Access Token을 Refresh Token으로 재발급한다.
 */
@Component
@RequiredArgsConstructor
public class JwtRefreshFilter extends OncePerRequestFilter {
    // Resource Server 인증 필터가 만료된 access cookie를 거부하기 전에 새 access cookie로 교체한다.

    private final JwtProperties jwtProperties;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final AppUserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = findCookieValue(request, jwtProperties.getAccessCookieName());
        if (jwtTokenService.resolveAccessTokenUserId(accessToken).isPresent()) {
            filterChain.doFilter(request, response);
            return;
        }

        String refreshToken = findCookieValue(request, jwtProperties.getRefreshCookieName());
        JwtTokenService.TokenDetails refreshDetails = jwtTokenService.resolveRefreshTokenDetails(refreshToken).orElse(null);
        if (refreshDetails == null
                || !refreshTokenStore.isValid(refreshDetails.tokenId(), refreshDetails.userId())
                || userRepository.findById(refreshDetails.userId()).isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String renewedAccessToken = jwtTokenService.createAccessToken(refreshDetails.userId());
        jwtTokenService.writeRefreshedAccessCookie(response, renewedAccessToken);
        filterChain.doFilter(wrapWithAccessToken(request, renewedAccessToken), response);
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

    private HttpServletRequest wrapWithAccessToken(HttpServletRequest request, String accessToken) {
        Cookie[] originalCookies = request.getCookies() == null ? new Cookie[0] : request.getCookies();
        Cookie renewedAccessCookie = new Cookie(jwtProperties.getAccessCookieName(), accessToken);
        Cookie[] cookiesWithoutOldAccess = Arrays.stream(originalCookies)
                .filter(cookie -> !jwtProperties.getAccessCookieName().equals(cookie.getName()))
                .toArray(Cookie[]::new);
        Cookie[] wrappedCookies = Arrays.copyOf(cookiesWithoutOldAccess, cookiesWithoutOldAccess.length + 1);
        wrappedCookies[wrappedCookies.length - 1] = renewedAccessCookie;

        return new HttpServletRequestWrapper(request) {
            @Override
            public Cookie[] getCookies() {
                return wrappedCookies;
            }
        };
    }
}
