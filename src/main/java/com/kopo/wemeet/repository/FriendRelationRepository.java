package com.kopo.wemeet.repository;

import com.kopo.wemeet.entity.FriendRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long> {
    List<FriendRelation> findAllByUserIdOrderByFriend_NicknameAsc(String userId);

    boolean existsByUserIdAndFriendId(String userId, String friendId);
}
