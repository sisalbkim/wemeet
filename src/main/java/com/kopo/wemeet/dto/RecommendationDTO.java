package com.kopo.wemeet.dto;

import java.util.List;

public final class RecommendationDTO {

    private RecommendationDTO() {
    }

    public record RecommendationRequest(
            String category,
            List<String> participantIds,
            String mode,
            String anchorParticipantId,
            String routeMode
    ) {
    }

    public record GuestRecommendationRequest(
            String baseAddress,
            String category,
            String mode,
            String anchorParticipantId,
            String routeMode
    ) {
    }

    public record RecommendationResponse(
            String category,
            List<UserDTO.UserResponse> participants,
            MidpointResponse midpoint,
            List<VenueResponse> venues,
            List<MapPointResponse> mapPoints,
            String calculationMode
    ) {
    }

    public record CategoryResponse(
            List<String> categories
    ) {
    }

    public record TravelTimeResponse(
            String participantId,
            String participantName,
            int minutes,
            List<RoutePointResponse> routePath,
            List<RouteModeResponse> routeModes
    ) {
        public TravelTimeResponse(String participantId, String participantName, int minutes) {
            this(participantId, participantName, minutes, List.of());
        }

        public TravelTimeResponse(String participantId, String participantName, int minutes, List<RoutePointResponse> routePath) {
            this(participantId, participantName, minutes, routePath, List.of(
                    new RouteModeResponse("car", "자동차", minutes, routePath, true)
            ));
        }
    }

    public record RouteModeResponse(
            String mode,
            String label,
            int minutes,
            List<RoutePointResponse> routePath,
            boolean available
    ) {
    }

    public record RoutePointResponse(
            double latitude,
            double longitude
    ) {
    }

    public record VenueResponse(
            String name,
            String category,
            String area,
            String station,
            double latitude,
            double longitude,
            String description,
            String telephone,
            String link,
            String reason,
            int fairnessGap,
            int averageMinutes,
            List<String> highlights,
            List<TravelTimeResponse> travelTimes
    ) {
    }

    public record MidpointResponse(
            String district,
            String station,
            double latitude,
            double longitude,
            int averageMinutes,
            int fairnessGap,
            String note
    ) {
    }

    public record MapPointResponse(
            String id,
            String label,
            String address,
            double latitude,
            double longitude,
            String markerType,
            boolean selected
    ) {
    }

    public record CategoryChip(
            String label,
            String value
    ) {
    }

    public record MidpointSummary(
            String district,
            String station,
            double latitude,
            double longitude,
            int averageMinutes,
            int fairnessGap,
            String note
    ) {
    }

    public record TravelTime(
            String participantId,
            String participantName,
            int minutes,
            List<RoutePointResponse> routePath,
            List<RouteModeResponse> routeModes
    ) {
        public TravelTime(String participantName, int minutes) {
            this("", participantName, minutes, List.of());
        }

        public TravelTime(String participantId, String participantName, int minutes, List<RoutePointResponse> routePath) {
            this(participantId, participantName, minutes, routePath, List.of(
                    new RouteModeResponse("car", "자동차", minutes, routePath, true)
            ));
        }
    }

    public record VenueOption(
            String name,
            String category,
            String area,
            double latitude,
            double longitude,
            String description,
            String telephone,
            String link,
            String reason,
            int fairnessGap,
            int averageMinutes,
            List<String> highlights,
            List<TravelTime> travelTimes
    ) {
    }

    public record MapPoint(
            String id,
            String label,
            String address,
            double latitude,
            double longitude,
            String markerType,
            boolean selected
    ) {
    }

    public record RecommendationBundle(
            String category,
            List<FriendDTO.FriendSummary> participants,
            MidpointSummary midpoint,
            List<VenueOption> venues,
            List<MapPoint> mapPoints,
            String calculationMode
    ) {
    }
}
