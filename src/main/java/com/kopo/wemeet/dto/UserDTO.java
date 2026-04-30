package com.kopo.wemeet.dto;

public final class UserDTO {
    // 사용자 프로필과 API 응답에 쓰는 DTO 모음이다.

    private UserDTO() {
    }

    public record UserResponse(
            String id,
            String nickname,
            String loginId,
            String email,
            String friendCode,
            String baseAddress
    ) {
    }

    public record AddressUpdateRequest(
            String baseAddress
    ) {
    }

    public record UserProfile(
            String id,
            String name,
            String handle,
            String friendCode,
            String baseAddress,
            String passwordMask,
            int createdMeetings,
            int friendCount,
            int joinedMeetings
    ) {
    }
}
