package com.kopo.wemeet.dto;

/**
 * 모임 참가자의 역할과 초대 응답 상태를 전달하는 DTO입니다.
 */
public record MeetingParticipantStatusDTO(
        String userId,
        String name,
        String addressHint,
        String role,
        String status,
        String statusLabel,
        String statusTone
) {
}
