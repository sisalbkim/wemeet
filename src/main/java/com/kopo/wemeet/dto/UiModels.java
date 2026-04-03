package com.kopo.wemeet.dto;

import java.util.List;

public final class UiModels {
    // Thymeleaf 템플릿이 직접 쓰는 화면 전용 모델 모음이다.
    // API DTO를 그대로 노출하지 않고, 화면에 필요한 필드만 묶어서 전달한다.

    private UiModels() {
    }

    // 프로필/친구/모임처럼 화면 상단 카드와 목록에 쓰이는 기본 모델들이다.
    public record UserProfile(
            String id,
            String name,
            String handle,
            String friendCode,
            String baseAddress,
            String passwordMask,
            int createdMeetings,
            int friendCount,
            int joinedMeetings
    ) {
    }

    public record FriendSummary(
            String id,
            String name,
            String handle,
            String addressHint,
            String joinedOn
    ) {
    }

    public record FriendRequest(
            String name,
            String handle
    ) {
    }

    public record UpcomingMeeting(
            String title,
            String description,
            String dateLabel,
            String hostName,
            String status,
            String statusTone
    ) {
    }

    public record SearchHistoryItem(
            Long id,
            String query,
            String category,
            String dateLabel
    ) {
    }

    public record CategoryChip(
            String label,
            String value
    ) {
    }

    // 추천 결과 화면에서 지도/카드/이동시간 영역을 채우는 모델들이다.
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
            String participantName,
            int minutes
    ) {
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
            List<FriendSummary> participants,
            MidpointSummary midpoint,
            List<VenueOption> venues,
            List<MapPoint> mapPoints,
            String calculationMode
    ) {
    }
}
