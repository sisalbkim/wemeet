package com.kopo.wemeet.dto;

/**
 * 홈 화면의 다가오는 모임 카드에 필요한 정보를 전달하는 DTO입니다.
 */
public record UpcomingMeetingDTO(
        String title,
        String description,
        String dateLabel,
        String hostName,
        String status,
        String statusTone,
        String meetingPlaceName,
        String meetingPlaceAddress,
        String naverMapUrl,
        String weather
) {
}
