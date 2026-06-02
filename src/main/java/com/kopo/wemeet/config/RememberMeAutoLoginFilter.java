package com.kopo.wemeet.config;

import com.kopo.wemeet.dto.AuthDTO;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.impl.RememberMeJwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Optional;

@Component
public class RememberMeAutoLoginFilter extends OncePerRequestFilter {
    // 세션이 없을 때만 자동로그인 쿠키를 확인해서 새 세션을 복원한다.

    private final IApiAuthService authService;
    private final RememberMeJwtService rememberMeJwtService;

    public RememberMeAutoLoginFilter(
            IApiAuthService authService,
            RememberMeJwtService rememberMeJwtService
    ) {
        this.authService = authService;
        this.rememberMeJwtService = rememberMeJwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (hasAuthenticatedSession(session) || !rememberMeJwtService.hasRememberMeCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<String> rememberedUserId = rememberMeJwtService.resolveUserIdFromCookie(request);
        if (rememberedUserId.isEmpty()) {
            rememberMeJwtService.clearRememberMeCookie(response);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthDTO.AuthResponse loginResult = authService.createSessionForUser(rememberedUserId.get());
            HttpSession loginSession = request.getSession(true);
            loginSession.setAttribute("AUTH_TOKEN", loginResult.token());
            loginSession.setAttribute("USER_ID", loginResult.user().id());
            loginSession.setAttribute("USER_NICKNAME", loginResult.user().nickname());
        } catch (ResponseStatusException exception) {
            rememberMeJwtService.clearRememberMeCookie(response);
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasAuthenticatedSession(HttpSession session) {
        return session != null && session.getAttribute("AUTH_TOKEN") instanceof String token && !token.isBlank();
    }
}
