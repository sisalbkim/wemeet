package com.kopo.wemeet.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * MeetingDTO는 계층 간 데이터 전달과 화면/API 응답 구성을 위한 DTO 묶음입니다.
 */
public final class MeetingDTO {
    // 모임 생성, 목록, 상세 응답에 공통으로 쓰는 DTO 모음이다.

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

    public record MeetingRecord(
            String id,
            String title,
            String description,
            LocalDate meetingDate,
            LocalTime meetingTime,
            String category,
            String meetingPlaceName,
            String meetingPlaceAddress,
            String recommendationMode,
            String anchorParticipantId,
            String recommendationSnapshotJson,
            LocalDateTime recommendationSnapshotExpiresAt,
            String hostUserId,
            List<String> participantIds,
            List<MeetingParticipantStatus> participantStatuses,
            LocalDateTime createdAt
    ) {
    }

    public record MeetingParticipantStatus(
            String userId,
            String name,
            String addressHint,
            String role,
            String status,
            String statusLabel,
            String statusTone
    ) {
    }

    public record MeetingInvitation(
            String meetingId,
            String title,
            String hostName,
            String dateLabel,
            String timeLabel,
            String meetingPlaceName
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
            int participantCount,
            String naverMapUrl
    ) {
    }
}
