package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.FriendDTO;
import com.kopo.wemeet.dto.UserDTO;

import java.util.List;

public interface IFriendService {
    // 친구 관계 조회와 요청 처리 로직을 담당한다.

    List<UserDTO.UserAccount> listFriends(String userId);

    UserDTO.UserAccount addFriendByCode(String userId, String friendCode);

    List<FriendDTO.FriendRequestEntry> listIncomingFriendRequests(String userId);

    List<FriendDTO.FriendRequestEntry> listOutgoingFriendRequests(String userId);

    UserDTO.UserAccount respondFriendRequest(String recipientUserId, String requesterUserId, boolean approve);

    void updateFriendFavorite(String userId, String friendId, boolean favorite);
}

