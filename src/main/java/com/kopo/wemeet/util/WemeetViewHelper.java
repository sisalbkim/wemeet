package com.kopo.wemeet.util;

import com.kopo.wemeet.config.OpenApiProperties;
import com.kopo.wemeet.config.NaverMapProperties;
import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static com.kopo.wemeet.util.UiDefaults.APP_NAME;
import static com.kopo.wemeet.util.UiDefaults.DEFAULT_ROUTE_MODE;
import static com.kopo.wemeet.util.UiDefaults.ROUTE_MODE_TRANSIT;
import static com.kopo.wemeet.util.UiDefaults.ROUTE_MODE_WALK;

@Component
public class WemeetViewHelper {
    // 화면 컨트롤러에서 공통으로 쓰는 로그인 체크와 모델 조립 보조 로직 모음이다.

    private final IApiAuthService authService;
    private final NaverMapProperties naverMapProperties;
    private final OpenApiProperties openApiProperties;
    private final boolean forceTransitVisible;

    public WemeetViewHelper(
            IApiAuthService authService,
            NaverMapProperties naverMapProperties,
            OpenApiProperties openApiProperties,
            @Value("${app.ui.force-transit-visible:false}") boolean forceTransitVisible
    ) {
        this.authService = authService;
        this.naverMapProperties = naverMapProperties;
        this.openApiProperties = openApiProperties;
        this.forceTransitVisible = forceTransitVisible;
    }

    public void populateCommon(Model model, String activeTab, boolean guestMode) {
        model.addAttribute("appName", APP_NAME);
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("guestMode", guestMode);
        model.addAttribute("naverMapEnabled", naverMapProperties.isEnabled());
        model.addAttribute("naverMapKeyId", naverMapProperties.getKeyId());
        model.addAttribute("tmapTransitEnabled", isTransitEnabled());
        model.addAttribute("transitOptionVisible", isTransitOptionVisible());
    }

    public boolean isTransitEnabled() {
        return openApiProperties.isEnabled() && openApiProperties.isOdsayConfigured();
    }

    public boolean isTransitOptionVisible() {
        return forceTransitVisible || isTransitEnabled();
    }

    public String normalizeRouteMode(String routeMode) {
        if (ROUTE_MODE_TRANSIT.equalsIgnoreCase(routeMode) && isTransitEnabled()) {
            return ROUTE_MODE_TRANSIT;
        }
        if (ROUTE_MODE_WALK.equalsIgnoreCase(routeMode)) {
            return routeMode.toLowerCase(Locale.ROOT);
        }
        return DEFAULT_ROUTE_MODE;
    }

    public AppUser findLoggedInUser(HttpSession session) {
        if (session == null) {
            return null;
        }

        String token = (String) session.getAttribute("AUTH_TOKEN");
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            return authService.requireUser("Bearer " + token);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 401) {
                session.invalidate();
                return null;
            }
            throw exception;
        }
    }

    public AppUser requireLoggedInUser(HttpSession session) {
        AppUser user = findLoggedInUser(session);
        if (user == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Login required");
        }
        return user;
    }

    public UserDTO.UserProfile toProfile(AppUser user) {
        return toProfile(user, 2, 2, 3);
    }

    public UserDTO.UserProfile toProfile(AppUser user, int createdMeetings, int friendCount, int joinedMeetings) {
        return new UserDTO.UserProfile(
                user.getId(),
                user.getNickname(),
                "@" + user.getFriendCode(),
                user.getFriendCode(),
                user.getBaseAddress(),
                "••••••••",
                createdMeetings,
                friendCount,
                joinedMeetings
        );
    }
}
