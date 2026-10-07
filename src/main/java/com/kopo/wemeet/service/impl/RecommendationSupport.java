package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.RecommendationDTO;

import java.util.List;
import java.util.Map;

/**
 * RecommendationSupport는 도메인 규칙과 외부 연동 흐름을 조합해 실제 비즈니스 처리를 수행합니다.
 */
public final class RecommendationSupport {
    private RecommendationSupport() {
    }

    public record VenueSelectionResult(
            List<VenueEvaluation> selected,
            List<VenueEvaluation> remaining
    ) {
    }

    public record VenueEvaluation(
            RecommendationDTO.VenueResponse response,
            double strategyScore,
            boolean usedFallbackRouting
    ) {
    }

    public record TravelResolution(
            Map<String, ParticipantRouteEstimate> travelByUserId,
            boolean usedFallbackRouting
    ) {
    }

    public record ParticipantRouteEstimate(
            int minutes,
            List<RecommendationDTO.RoutePointResponse> routePath,
            List<RecommendationDTO.RouteModeResponse> routeModes
    ) {
        public ParticipantRouteEstimate(int minutes, List<RecommendationDTO.RoutePointResponse> routePath) {
            this(minutes, routePath, List.of(
                    new RecommendationDTO.RouteModeResponse("car", "자동차", minutes, routePath, true)
            ));
        }
    }

    public record SearchAnchor(
            String query,
            GeoPoint point
    ) {
    }

    public record GeoPoint(
            double latitude,
            double longitude
    ) {
    }

    public record ParticipantProfile(
            String id,
            String nickname,
            String baseAddress
    ) {
    }

    public enum RoutePreference {
        CAR("car"),
        TRANSIT("transit"),
        WALK("walk");

        private final String mode;

        RoutePreference(String mode) {
            this.mode = mode;
        }

        public String mode() {
            return mode;
        }

        public static RoutePreference from(String value) {
            if ("transit".equalsIgnoreCase(value)) {
                return TRANSIT;
            }
            if ("walk".equalsIgnoreCase(value)) {
                return WALK;
            }
            return CAR;
        }
    }
}
