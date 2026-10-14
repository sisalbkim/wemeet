package com.kopo.wemeet.dto;

/**
 * 프로필 화면의 생성/참여 모임 카드에 필요한 정보를 전달하는 DTO입니다.
 */
public record CreatedMeetingDTO(
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
