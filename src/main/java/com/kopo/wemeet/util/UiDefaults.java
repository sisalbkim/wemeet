package com.kopo.wemeet.util;

import com.kopo.wemeet.dto.RecommendationMode;

/**
 * UiDefaults는 여러 계층에서 반복되는 화면/문자열 처리 로직을 모아 둔 유틸리티입니다.
 */
public final class UiDefaults {
    // 화면 컨트롤러와 템플릿 모델에서 공유하는 기본 UI 값을 한 곳에 모은다.

    private UiDefaults() {
    }

    public static final String APP_NAME = "모임 장소 찾기";

    public static final String DEFAULT_CATEGORY = "맛집";
    public static final String DEFAULT_ROUTE_MODE = "car";
    public static final String ROUTE_MODE_TRANSIT = "transit";
    public static final String ROUTE_MODE_WALK = "walk";
    public static final String DEFAULT_RECOMMENDATION_MODE = RecommendationMode.CENTER_NAME;
    public static final String DEFAULT_GUEST_ADDRESS = "서울특별시 중구 명동길 74";
    public static final int DEFAULT_PLACE_DISPLAY_COUNT = 5;

    public static final String TAB_HOME = "home";
    public static final String TAB_CREATE = "create";
    public static final String TAB_NEARBY = "nearby";
    public static final String TAB_GUEST_HOME = "guest-home";
    public static final String TAB_PROFILE = "profile";
}
