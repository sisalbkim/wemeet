package com.kopo.wemeet.dto;

import java.util.List;

public final class ApiDtos {
    // REST API 요청/응답 payload를 모아 둔 record 모음이다.
    // 컨트롤러와 서비스가 같은 타입을 공유하도록 한 파일에 정리했다.

    private ApiDtos() {
    }

    // 인증/회원/친구/모임 생성에 쓰이는 요청 DTO들이다.
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
            String meetingTime,
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

    public record GuestRecommendationRequest(
            String baseAddress,
            String category,
            String mode,
            String anchorParticipantId
    ) {
    }

    public record PlaceSearchRequest(
            String originQuery,
            String tag,
            Integer display
    ) {
    }

    // 로그인한 사용자 정보나 인증 결과처럼 기본 API 응답에 자주 쓰이는 DTO들이다.
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

    public record EmailAvailabilityResponse(
            boolean available,
            String message
    ) {
    }

    public record EmailVerificationSendRequest(
            String email
    ) {
    }

    public record EmailVerificationSendResponse(
            boolean sent,
            String message,
            String codePreview
    ) {
    }

    public record EmailVerificationConfirmRequest(
            String email,
            String code
    ) {
    }

    public record EmailVerificationConfirmResponse(
            boolean verified,
            String message
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
            String meetingTime,
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

    // 추천 결과와 네이버 장소 검색 결과에 쓰이는 상세 응답 DTO들이다.
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

    public record PlaceSearchOriginResponse(
            String query,
            String name,
            String address,
            double latitude,
            double longitude
    ) {
    }

    public record PlaceRoutePointResponse(
            double latitude,
            double longitude
    ) {
    }

    public record PlaceCandidateResponse(
            String name,
            String normalizedCategory,
            String category,
            String address,
            String roadAddress,
            String telephone,
            String link,
            double latitude,
            double longitude,
            int distanceMeters,
            int durationMinutes,
            List<PlaceRoutePointResponse> routePath
    ) {
    }

    public record PlaceSearchResponse(
            String combinedQuery,
            String requestedTag,
            String normalizedTag,
            List<String> appliedQueryTerms,
            List<String> observedCategories,
            PlaceSearchOriginResponse origin,
            List<PlaceCandidateResponse> places
    ) {
    }

    public record PlaceTagResponse(
            String label,
            List<String> queryTerms
    ) {
    }

    public record PlaceTagCatalogResponse(
            List<PlaceTagResponse> tags
    ) {
    }
}
