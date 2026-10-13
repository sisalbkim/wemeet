package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.MeetingCreateRequestDTO;
import com.kopo.wemeet.dto.MeetingRecordDTO;
import com.kopo.wemeet.dto.MeetingResponseDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * IMeetingService는 구현체가 지켜야 할 서비스 계층 계약을 정의합니다.
 */
public interface IMeetingService {
    // 모임 생성, 조회, 삭제를 모아두는 서비스 계약이다.

    MeetingRecordDTO createMeeting(
            String hostUserId,
            String title,
            String description,
            LocalDate meetingDate,
            LocalTime meetingTime,
            String category,
            List<String> participantIds
    );

    MeetingRecordDTO createMeeting(
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

    MeetingRecordDTO createMeeting(
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

    List<MeetingRecordDTO> listMeetingsForUser(String userId);

    List<MeetingRecordDTO> listPendingInvitationsForUser(String userId);

    List<MeetingRecordDTO> listMeetingsCreatedByUser(String userId);

    Optional<MeetingRecordDTO> findMeetingCreatedByUser(String userId, String meetingId);

    Optional<MeetingRecordDTO> findMeetingForUser(String userId, String meetingId);

    void deleteMeetingCreatedByUser(String userId, String meetingId);

    void respondMeetingInvitation(String userId, String meetingId, boolean accept);

    List<MeetingResponseDTO> getApiMeetings(String userId);

    MeetingResponseDTO createApiMeeting(String requesterId, MeetingCreateRequestDTO request);
}
