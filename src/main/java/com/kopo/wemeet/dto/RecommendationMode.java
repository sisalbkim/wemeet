package com.kopo.wemeet.dto;

public enum RecommendationMode {
    CENTER("중심점 부근"),
    ANCHOR("특정 인물 근처"),
    RANDOM("랜덤");

    private final String label;

    RecommendationMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static RecommendationMode from(String value) {
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
