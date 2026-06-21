package com.kopo.wemeet.dto;

/**
 * RecommendationMode는 계층 간 데이터 전달과 화면/API 응답 구성을 위한 DTO 묶음입니다.
 */
public enum RecommendationMode {
    // 추천 결과를 어떤 기준으로 고를지 결정하는 전략 enum이다.
    CENTER("중심점 부근"),
    ANCHOR("특정 인물 근처"),
    RANDOM("랜덤");

    private final String label;

    public static final String CENTER_NAME = "CENTER";

    RecommendationMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static RecommendationMode from(String value) {
        // 외부 입력 문자열이 비어 있거나 잘못돼도 기본 전략(CENTER)으로 안전하게 복구한다.
        if (value == null || value.isBlank()) {
            return CENTER;
        }

        for (RecommendationMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return CENTER;
    }
}
