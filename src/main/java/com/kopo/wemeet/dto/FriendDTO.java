package com.kopo.wemeet.dto;

public final class FriendDTO {

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
