package com.kopo.wemeet.dto;

/**
 * 사용자가 받은 모임 초대 목록 화면에 필요한 정보를 전달하는 DTO입니다.
 */
public record MeetingInvitationDTO(
        String meetingId,
        String title,
        String hostName,
        String dateLabel,
        String timeLabel,
        String meetingPlaceName
) {
}
