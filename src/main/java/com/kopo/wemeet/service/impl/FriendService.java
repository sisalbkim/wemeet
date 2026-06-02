package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.FriendDTO;
import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.FriendRelationRepository;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.repository.entity.FriendRelation;
import com.kopo.wemeet.repository.entity.FriendRelation.FriendStatus;
import com.kopo.wemeet.service.IFriendService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class FriendService implements IFriendService {
    // 친구 목록 조회, 요청 생성, 승인/거절 흐름을 전담한다.

    private final AppUserRepository userRepository;
    private final FriendRelationRepository friendRelationRepository;

    public FriendService(
            AppUserRepository userRepository,
            FriendRelationRepository friendRelationRepository
    ) {
        this.userRepository = userRepository;
        this.friendRelationRepository = friendRelationRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<UserDTO.UserAccount> listFriends(String userId) {
        return friendRelationRepository.findAllByUserIdAndStatusOrderByFriend_NicknameAsc(userId, FriendStatus.ACCEPTED).stream()
                .sorted(Comparator
                        .comparing(FriendRelation::isFavorite).reversed()
                        .thenComparing(relation -> relation.getFriend().getNickname()))
                .map(relation -> toUserAccount(relation.getFriend(), relation.isFavorite()))
                .toList();
    }

    @Transactional
    @Override
    public UserDTO.UserAccount addFriendByCode(String userId, String friendCode) {
        if (friendCode == null || friendCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "friendCode is required");
        }

        String normalizedFriendCode = friendCode.trim().replaceFirst("^@", "");
        AppUser user = requireUser(userId);
        AppUser friend = userRepository.findByFriendCode(normalizedFriendCode)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Friend code not found"));

        if (user.getId().equals(friend.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "You cannot add yourself");
        }

        if (friendRelationRepository.existsByUserIdAndFriendIdAndStatus(user.getId(), friend.getId(), FriendStatus.ACCEPTED)
                || friendRelationRepository.existsByUserIdAndFriendIdAndStatus(friend.getId(), user.getId(), FriendStatus.ACCEPTED)) {
            throw new ResponseStatusException(CONFLICT, "Friend already added");
        }

        if (friendRelationRepository.existsByUserIdAndFriendIdAndStatus(user.getId(), friend.getId(), FriendStatus.PENDING)) {
            throw new ResponseStatusException(CONFLICT, "Friend request already sent");
        }

        if (friendRelationRepository.existsByUserIdAndFriendIdAndStatus(friend.getId(), user.getId(), FriendStatus.PENDING)) {
            throw new ResponseStatusException(CONFLICT, "Friend request already received");
        }

        friendRelationRepository.save(FriendRelation.pending(user, friend));
        return toUserAccount(friend);
    }

    @Transactional(readOnly = true)
    @Override
    public List<FriendDTO.FriendRequestEntry> listIncomingFriendRequests(String userId) {
        return friendRelationRepository.findAllByFriendIdAndStatusOrderByUser_NicknameAsc(userId, FriendStatus.PENDING).stream()
                .map(relation -> toFriendRequestEntry(relation.getUser(), relation.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<FriendDTO.FriendRequestEntry> listOutgoingFriendRequests(String userId) {
        return friendRelationRepository.findAllByUserIdAndStatusOrderByFriend_NicknameAsc(userId, FriendStatus.PENDING).stream()
                .map(relation -> toFriendRequestEntry(relation.getFriend(), relation.getCreatedAt()))
                .toList();
    }

    @Transactional
    @Override
    public UserDTO.UserAccount respondFriendRequest(String recipientUserId, String requesterUserId, boolean approve) {
        if (requesterUserId == null || requesterUserId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "requesterId is required");
        }

        AppUser recipient = requireUser(recipientUserId);
        AppUser requester = requireUser(requesterUserId);
        FriendRelation request = friendRelationRepository
                .findByUserIdAndFriendIdAndStatus(requester.getId(), recipient.getId(), FriendStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Friend request not found"));

        if (!approve) {
            friendRelationRepository.delete(request);
            return toUserAccount(requester);
        }

        request.accept();
        if (!friendRelationRepository.existsByUserIdAndFriendIdAndStatus(recipient.getId(), requester.getId(), FriendStatus.ACCEPTED)) {
            friendRelationRepository.save(new FriendRelation(recipient, requester));
        }
        return toUserAccount(requester);
    }

    @Transactional
    @Override
    public void updateFriendFavorite(String userId, String friendId, boolean favorite) {
        FriendRelation relation = friendRelationRepository.findByUserIdAndFriendIdAndStatus(userId, friendId, FriendStatus.ACCEPTED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Friend relation not found"));
        relation.changeFavorite(favorite);
    }

    private AppUser requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found: " + userId));
    }

    private UserDTO.UserAccount toUserAccount(AppUser user) {
        return toUserAccount(user, false);
    }

    private UserDTO.UserAccount toUserAccount(AppUser user, boolean favorite) {
        LocalDate joinedOn = user.getCreatedAt() == null ? LocalDate.now() : user.getCreatedAt().toLocalDate();
        return new UserDTO.UserAccount(
                user.getId(),
                user.getNickname(),
                user.getLoginId(),
                "",
                user.getFriendCode(),
                user.getBaseAddress(),
                joinedOn,
                favorite
        );
    }

    private FriendDTO.FriendRequestEntry toFriendRequestEntry(AppUser user, LocalDateTime requestedAt) {
        LocalDate requestedOn = requestedAt == null ? LocalDate.now() : requestedAt.toLocalDate();
        return new FriendDTO.FriendRequestEntry(
                user.getId(),
                user.getNickname(),
                user.getFriendCode(),
                user.getBaseAddress(),
                requestedOn
        );
    }
}

