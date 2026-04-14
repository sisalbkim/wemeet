package com.kopo.wemeet.repository;

import com.kopo.wemeet.entity.FriendRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long> {
    // 특정 사용자의 친구 목록 조회와 중복 관계 확인에 쓰는 Repository다.

    List<FriendRelation> findAllByUserIdOrderByFriend_NicknameAsc(String userId);

    boolean existsByUserIdAndFriendId(String userId, String friendId);

    Optional<FriendRelation> findByUserIdAndFriendId(String userId, String friendId);
}
