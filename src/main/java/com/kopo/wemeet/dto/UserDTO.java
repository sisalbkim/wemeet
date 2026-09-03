package com.kopo.wemeet.dto;

import java.time.LocalDate;

/**
 * UserDTO는 계층 간 데이터 전달과 화면/API 응답 구성을 위한 DTO 묶음입니다.
 */
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
            String baseAddress,
            String profileImageUrl
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
            String profileImageUrl,
            String passwordMask,
            int createdMeetings,
            int friendCount,
            int joinedMeetings
    ) {
    }

    public record UserAccount(
            String id,
            String nickname,
            String loginId,
            String password,
            String friendCode,
            String baseAddress,
            LocalDate joinedOn,
            boolean favorite
    ) {
    }
}
