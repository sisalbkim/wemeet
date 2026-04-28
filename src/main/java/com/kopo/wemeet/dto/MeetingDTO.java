package com.kopo.wemeet.dto;

import java.util.List;

public final class MeetingDTO {

    private MeetingDTO() {
    }

    public record MeetingCreateRequest(
            String title,
            String description,
            String meetingDate,
            String meetingTime,
            String category,
            String meetingPlaceName,
            String meetingPlaceAddress,
            List<String> participantIds
    ) {
    }

    public record MeetingResponse(
            String id,
            String title,
            String description,
            String meetingDate,
            String meetingTime,
            String category,
            String meetingPlaceName,
            String meetingPlaceAddress,
            UserDTO.UserResponse host,
            List<UserDTO.UserResponse> participants
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

    public record CreatedMeeting(
            String id,
            String title,
            String description,
            String dateLabel,
            String timeLabel,
            String category,
            String meetingPlaceName,
            String meetingPlaceAddress,
            int participantCount
    ) {
    }
}
