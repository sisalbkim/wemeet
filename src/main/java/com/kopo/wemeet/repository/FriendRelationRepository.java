package com.kopo.wemeet.repository;

import com.kopo.wemeet.repository.entity.FriendRelation;
import com.kopo.wemeet.repository.entity.FriendRelation.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long> {
    // 특정 사용자의 친구 목록 조회와 중복 관계 확인에 쓰는 Repository다.

    List<FriendRelation> findAllByUserIdOrderByFriend_NicknameAsc(String userId);

    List<FriendRelation> findAllByUserIdAndStatusOrderByFriend_NicknameAsc(String userId, FriendStatus status);

    List<FriendRelation> findAllByFriendIdAndStatusOrderByUser_NicknameAsc(String friendId, FriendStatus status);

    boolean existsByUserIdAndFriendId(String userId, String friendId);

    boolean existsByUserIdAndFriendIdAndStatus(String userId, String friendId, FriendStatus status);

    Optional<FriendRelation> findByUserIdAndFriendId(String userId, String friendId);

    Optional<FriendRelation> findByUserIdAndFriendIdAndStatus(String userId, String friendId, FriendStatus status);
}
