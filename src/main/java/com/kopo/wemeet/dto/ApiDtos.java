package com.kopo.wemeet.dto;

import java.util.List;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record SignUpRequest(
            String nickname,
            String loginId,
            String password,
            String email,
            String baseAddress
    ) {
    }

    public record LoginRequest(
            String loginId,
            String password
    ) {
    }

    public record FriendAddRequest(
            String friendCode
    ) {
    }

    public record PasswordResetRequest(
            String email
    ) {
    }

    public record PasswordResetConfirmRequest(
            String token,
            String newPassword
    ) {
    }

    public record AddressUpdateRequest(
            String baseAddress
    ) {
    }

    public record MeetingCreateRequest(
            String title,
            String description,
            String meetingDate,
            String category,
            List<String> participantIds
    ) {
    }

    public record RecommendationRequest(
            String category,
            List<String> participantIds,
            String mode,
            String anchorParticipantId
    ) {
    }

    public record UserResponse(
            String id,
            String nickname,
            String loginId,
            String email,
            String friendCode,
            String baseAddress
    ) {
    }

    public record AuthResponse(
            String token,
            UserResponse user
    ) {
    }

    public record PasswordResetResponse(
            String message,
            String resetTokenPreview
    ) {
    }

    public record SearchHistoryResponse(
            String query,
            String category,
            String searchedAt
    ) {
    }

    public record MeetingResponse(
            String id,
            String title,
            String description,
            String meetingDate,
            String category,
            UserResponse host,
            List<UserResponse> participants
    ) {
    }

    public record TravelTimeResponse(
            String participantId,
            String participantName,
            int minutes
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

    public record RecommendationResponse(
            String category,
            List<UserResponse> participants,
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
}
