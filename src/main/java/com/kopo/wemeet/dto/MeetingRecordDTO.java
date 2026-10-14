package com.kopo.wemeet.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 저장된 모임의 상세 상태와 참가자 정보를 서비스 계층에서 전달하는 DTO입니다.
 */
public record MeetingRecordDTO(
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
        List<MeetingParticipantStatusDTO> participantStatuses,
        LocalDateTime createdAt
) {
}
