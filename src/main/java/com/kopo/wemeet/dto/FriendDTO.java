package com.kopo.wemeet.dto;

import java.time.LocalDate;

/**
 * FriendDTO는 계층 간 데이터 전달과 화면/API 응답 구성을 위한 DTO 묶음입니다.
 */
public final class FriendDTO {
    // 친구 목록, 친구 요청, 친구 추가 응답에 쓰는 DTO 모음이다.

    private FriendDTO() {
    }

    public record FriendAddRequest(
            String friendCode
    ) {
    }

    public record FriendDeleteRequest(
            String friendId
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

    public record FriendRequestEntry(
            String id,
            String nickname,
            String friendCode,
            String baseAddress,
            LocalDate requestedOn
    ) {
    }
}
