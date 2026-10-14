package com.kopo.wemeet.dto;

import java.util.List;

/**
 * 모임 API 응답 데이터를 전달하는 DTO입니다.
 */
public record MeetingResponseDTO(
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
