package com.kopo.wemeet.dto;

import java.util.List;

/**
 * 모임 생성 API 요청 데이터를 전달하는 DTO입니다.
 */
public record MeetingCreateRequestDTO(
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
