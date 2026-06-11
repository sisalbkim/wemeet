package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.MeetingDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface IMeetingService {
    // 모임 생성, 조회, 삭제를 모아두는 서비스 계약이다.

    MeetingDTO.MeetingRecord createMeeting(
            String hostUserId,
            String title,
            String description,
            LocalDate meetingDate,
            LocalTime meetingTime,
            String category,
            List<String> participantIds
    );

    MeetingDTO.MeetingRecord createMeeting(
            String hostUserId,
            String title,
            String description,
            LocalDate meetingDate,
            LocalTime meetingTime,
            String category,
            String meetingPlaceName,
            String meetingPlaceAddress,
            List<String> participantIds
    );

    MeetingDTO.MeetingRecord createMeeting(
            String hostUserId,
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
            List<String> participantIds
    );

    List<MeetingDTO.MeetingRecord> listMeetingsForUser(String userId);

    List<MeetingDTO.MeetingRecord> listPendingInvitationsForUser(String userId);

    List<MeetingDTO.MeetingRecord> listMeetingsCreatedByUser(String userId);

    Optional<MeetingDTO.MeetingRecord> findMeetingCreatedByUser(String userId, String meetingId);

    Optional<MeetingDTO.MeetingRecord> findMeetingForUser(String userId, String meetingId);

    void deleteMeetingCreatedByUser(String userId, String meetingId);

    void respondMeetingInvitation(String userId, String meetingId, boolean accept);

    List<MeetingDTO.MeetingResponse> getApiMeetings(String userId);

    MeetingDTO.MeetingResponse createApiMeeting(String requesterId, MeetingDTO.MeetingCreateRequest request);
}
