package com.kopo.wemeet.util;

import com.kopo.wemeet.config.OpenApiProperties;
import com.kopo.wemeet.config.NaverMapProperties;
import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static com.kopo.wemeet.util.UiDefaults.APP_NAME;
import static com.kopo.wemeet.util.UiDefaults.DEFAULT_ROUTE_MODE;
import static com.kopo.wemeet.util.UiDefaults.ROUTE_MODE_TRANSIT;
import static com.kopo.wemeet.util.UiDefaults.ROUTE_MODE_WALK;

/**
 * WemeetViewHelper는 여러 계층에서 반복되는 화면/문자열 처리 로직을 모아 둔 유틸리티입니다.
 */
@Component
public class WemeetViewHelper {

    // 인증 관련 서비스
    // Access/Refresh Token을 이용해 현재 로그인한 사용자를 찾을 때 사용
    private final IApiAuthService authService;

    // 네이버 지도 API 설정값
    private final NaverMapProperties naverMapProperties;

    // ODsay 등 외부 API 설정값
    private final OpenApiProperties openApiProperties;

    // 대중교통 기능을 화면에 강제로 표시할지 여부
    private final boolean forceTransitVisible;

    // Access Token / Refresh Token이 저장된 쿠키 이름
    private final String accessCookieName;
    private final String refreshCookieName;


    public WemeetViewHelper(
            IApiAuthService authService,
            NaverMapProperties naverMapProperties,
            OpenApiProperties openApiProperties,
            @Value("${app.ui.force-transit-visible:false}") boolean forceTransitVisible,
            @Value("${app.auth.jwt.access-cookie-name:WM_ACCESS_TOKEN}") String accessCookieName,
            @Value("${app.auth.jwt.refresh-cookie-name:WM_REFRESH_TOKEN}") String refreshCookieName
    ) {
        // Spring이 전달한 서비스와 설정값을 필드에 저장
        this.authService = authService;
        this.naverMapProperties = naverMapProperties;
        this.openApiProperties = openApiProperties;
        this.forceTransitVisible = forceTransitVisible;
        this.accessCookieName = accessCookieName;
        this.refreshCookieName = refreshCookieName;
    }


    // 여러 화면에서 공통으로 필요한 값을 Model에 추가
    // HTML에서는 ${appName}, ${activeTab} 등의 형태로 사용할 수 있음
    public void populateCommon(Model model, String activeTab, boolean guestMode) {
        model.addAttribute("appName", APP_NAME);
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("guestMode", guestMode);
        model.addAttribute("naverMapEnabled", naverMapProperties.isEnabled());
        model.addAttribute("naverMapKeyId", naverMapProperties.getKeyId());
        model.addAttribute("tmapTransitEnabled", isTransitEnabled());
        model.addAttribute("transitOptionVisible", isTransitOptionVisible());
    }


    // 외부 API가 활성화되어 있고 ODsay 설정도 되어 있으면
    // 대중교통 기능 사용 가능
    public boolean isTransitEnabled() {
        return openApiProperties.isEnabled()
                && openApiProperties.isOdsayConfigured();
    }


    // 강제 표시 설정이 켜져 있거나 실제 대중교통 기능을 사용할 수 있으면
    // 화면에 대중교통 옵션 표시
    public boolean isTransitOptionVisible() {
        return forceTransitVisible || isTransitEnabled();
    }


    // 사용자가 선택한 이동수단 값을 검사해서
    // 사용할 수 있는 정상적인 값으로 변환
    public String normalizeRouteMode(String routeMode) {

        // 대중교통을 선택했고 실제 대중교통 API 사용이 가능하면 그대로 사용
        if (ROUTE_MODE_TRANSIT.equalsIgnoreCase(routeMode)
                && isTransitEnabled()) {
            return ROUTE_MODE_TRANSIT;
        }

        // 도보를 선택했다면 소문자로 통일
        if (ROUTE_MODE_WALK.equalsIgnoreCase(routeMode)) {
            return routeMode.toLowerCase(Locale.ROOT);
        }

        // 올바른 값이 아니면 기본 이동수단 사용
        return DEFAULT_ROUTE_MODE;
    }


    // 현재 로그인한 사용자를 찾는 메서드
    // 1. Access Token 확인
    // 2. Access Token이 만료되었다면 Refresh Token으로 재발급 시도
    public AppUser findLoggedInUser(HttpSession session) {

        // 브라우저 쿠키에서 Access Token 가져오기
        String token = findAccessTokenCookie();

        if (token != null && !token.isBlank()) {
            try {
                // Access Token으로 현재 사용자 조회
                return authService.requireUser("Bearer " + token);

            } catch (ResponseStatusException exception) {

                // 401이 아니라 다른 오류라면 그대로 예외 발생
                if (exception.getStatusCode().value() != 401) {
                    throw exception;
                }

                // 401이면 Access Token이 만료되었을 가능성이 있으므로
                // 아래에서 Refresh Token 확인
            }
        }

        try {
            // Access Token을 사용할 수 없으면 Refresh Token 확인
            String refreshToken = findRefreshTokenCookie();

            // 새 Access Token을 쿠키에 넣기 위해 현재 응답 객체 가져오기
            HttpServletResponse response = currentResponse();

            // Refresh Token이 없으면 로그인 상태로 볼 수 없음
            if (refreshToken == null
                    || refreshToken.isBlank()
                    || response == null) {
                return null;
            }

            // Refresh Token으로 새로운 Access Token 발급 후 사용자 반환
            return authService.refreshAccessToken(refreshToken, response);

        } catch (ResponseStatusException exception) {

            // Refresh Token까지 유효하지 않다면 로그인 상태 해제
            if (exception.getStatusCode().value() == 401) {
                if (session != null) {
                    session.invalidate();
                }
                return null;
            }

            throw exception;
        }
    }


    // 반드시 로그인이 필요한 화면에서 사용
    public AppUser requireLoggedInUser(HttpSession session) {

        // 현재 로그인 사용자 조회
        AppUser user = findLoggedInUser(session);

        // 로그인 사용자를 찾지 못하면 401 오류
        if (user == null) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Login required"
            );
        }

        return user;
    }


    // 사용자 엔티티를 화면에 보여줄 UserProfile 형태로 변환
    // 모임/친구 수를 따로 전달하지 않으면 기본값 사용
    public UserDTO.UserProfile toProfile(AppUser user) {
        return toProfile(user, 2, 2, 3);
    }


    // AppUser(DB 사용자 정보)를
    // HTML에서 사용하기 편한 UserProfile DTO로 변환
    public UserDTO.UserProfile toProfile(
            AppUser user,
            int createdMeetings,
            int friendCount,
            int joinedMeetings
    ) {
        return new UserDTO.UserProfile(
                user.getId(),
                user.getNickname(),
                "@" + user.getFriendCode(),
                user.getFriendCode(),
                user.getBaseAddress(),
                user.getProfileImageUrl(),

                // ★ 프로필 이미지 기능 구현 시 여기에 이미지 주소도 전달 예정

                "••••••••",
                createdMeetings,
                friendCount,
                joinedMeetings
        );
    }


    // Access Token 쿠키의 값을 가져옴
    private String findAccessTokenCookie() {
        return findCookieValue(accessCookieName);
    }


    // Refresh Token 쿠키의 값을 가져옴
    private String findRefreshTokenCookie() {
        return findCookieValue(refreshCookieName);
    }


    // 현재 요청에 포함된 쿠키들 중
    // 전달받은 이름과 일치하는 쿠키 값을 찾아 반환
    private String findCookieValue(String cookieName) {

        // 현재 HTTP 요청 정보 가져오기
        ServletRequestAttributes attributes =
                (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();

        if (attributes == null
                || cookieName == null
                || cookieName.isBlank()) {
            return null;
        }

        // 현재 HTTP 요청 객체
        HttpServletRequest request = attributes.getRequest();

        // 브라우저가 보낸 모든 쿠키 가져오기
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        // 원하는 이름의 쿠키 찾기
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        // 해당 쿠키가 없으면 null
        return null;
    }


    // 현재 HTTP 응답 객체를 가져옴
    // Refresh Token으로 Access Token을 재발급했을 때
    // 새 토큰을 응답 쿠키에 저장하기 위해 사용
    private HttpServletResponse currentResponse() {

        ServletRequestAttributes attributes =
                (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();

        // attributes가 없으면 null,
        // 있으면 현재 HTTP Response 반환
        return attributes == null
                ? null
                : attributes.getResponse();
    }
}