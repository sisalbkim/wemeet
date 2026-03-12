package com.kopo.wemeet.ui;

import java.util.List;

public final class UiModels {

    private UiModels() {
    }

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

    public record MidpointSummary(
            String district,
            String station,
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
            String description,
            String reason,
            int fairnessGap,
            int averageMinutes,
            List<String> highlights,
            List<TravelTime> travelTimes
    ) {
    }

    public record RecommendationBundle(
            String category,
            List<FriendSummary> participants,
            MidpointSummary midpoint,
            List<VenueOption> venues,
            String calculationMode
    ) {
    }
}
