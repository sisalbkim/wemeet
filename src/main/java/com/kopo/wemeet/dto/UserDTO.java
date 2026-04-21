package com.kopo.wemeet.dto;

public final class UserDTO {

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
