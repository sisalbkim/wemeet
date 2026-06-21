package com.kopo.wemeet.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
/**
 * FriendRelation는 데이터베이스 테이블과 매핑되는 JPA 엔티티입니다.
 */
public class FriendRelation {
    // 사용자와 친구 사이의 단방향 관계 또는 친구 요청 한 건을 저장한다.
    // 승인 전에는 요청자 -> 수신자 PENDING 한 행만 두고, 승인 시 양방향 ACCEPTED 행으로 확정한다.

    public enum FriendStatus {
        PENDING,
        ACCEPTED
    }

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

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean favorite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'ACCEPTED'")
    private FriendStatus status = FriendStatus.ACCEPTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private AppUser requestedBy;

    protected FriendRelation() {
    }

    public FriendRelation(AppUser user, AppUser friend) {
        this.user = user;
        this.friend = friend;
        this.status = FriendStatus.ACCEPTED;
    }

    public static FriendRelation pending(AppUser requester, AppUser recipient) {
        FriendRelation relation = new FriendRelation();
        relation.user = requester;
        relation.friend = recipient;
        relation.requestedBy = requester;
        relation.status = FriendStatus.PENDING;
        return relation;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public FriendStatus getStatus() {
        return status;
    }

    public AppUser getRequestedBy() {
        return requestedBy;
    }

    public void changeFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void accept() {
        this.status = FriendStatus.ACCEPTED;
        this.requestedBy = null;
    }
}
