package com.kopo.wemeet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "friend_relation",
        uniqueConstraints = @UniqueConstraint(name = "uk_friend_relation_user_friend", columnNames = {"user_id", "friend_user_id"})
)
public class FriendRelation {
    // 사용자와 친구 사이의 단방향 관계 한 건을 저장한다.
    // 친구 추가 시 (A -> B), (B -> A) 두 행을 만들어 양방향처럼 동작시킨다.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "friend_user_id", nullable = false)
    private AppUser friend;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected FriendRelation() {
    }

    public FriendRelation(AppUser user, AppUser friend) {
        this.user = user;
        this.friend = friend;
    }

    @PrePersist
    void onCreate() {
        // 관계가 생성된 시각을 자동으로 남긴다.
        createdAt = LocalDateTime.now();
    }

    public AppUser getUser() {
        return user;
    }

    public AppUser getFriend() {
        return friend;
    }
}
