package com.kopo.wemeet.dto;

public final class FriendDTO {
    // 친구 목록, 친구 요청, 친구 추가 응답에 쓰는 DTO 모음이다.

    private FriendDTO() {
    }

    public record FriendAddRequest(
            String friendCode
    ) {
    }

    public record FriendSummary(
            String id,
            String name,
            String handle,
            String addressHint,
            String joinedOn,
            boolean favorite
    ) {
    }

    public record FriendRequest(
            String id,
            String name,
            String handle,
            String addressHint,
            String requestedOn
    ) {
    }
}
