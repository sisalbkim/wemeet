package com.kopo.wemeet.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
public class AppUser {
    // 회원 기본 정보를 저장하는 JPA 엔티티다.
    // 추천 계산에 필요한 출발지 주소와 친구코드도 함께 보관한다.

    @Id
    @Column(length = 40, nullable = false)
    private String id;

    @Column(length = 60, nullable = false, unique = true)
    private String loginId;

    @Column(length = 80, nullable = false)
    private String nickname;

    @Column(length = 120, nullable = false, unique = true)
    private String email;

    @Column(length = 120, nullable = false)
    private String passwordHash;

    @Column(length = 40, nullable = false, unique = true)
    private String friendCode;

    @Column(length = 255, nullable = false)
    private String baseAddress;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AppUser() {
    }

    public AppUser(
            String id,
            String loginId,
            String nickname,
            String email,
            String passwordHash,
            String friendCode,
            String baseAddress
    ) {
        this.id = id;
        this.loginId = loginId;
        this.nickname = nickname;
        this.email = email;
        this.passwordHash = passwordHash;
        this.friendCode = friendCode;
        this.baseAddress = baseAddress;
    }

    @PrePersist
    void onCreate() {
        // 최초 저장 시 생성/수정 시각을 동시에 기록한다.
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        // 엔티티가 수정될 때마다 updatedAt을 최신 시각으로 덮어쓴다.
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFriendCode() {
        return friendCode;
    }

    public String getBaseAddress() {
        return baseAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void changeBaseAddress(String baseAddress) {
        // 추천 출발지로 쓰이는 기본 주소 변경용 메서드다.
        this.baseAddress = baseAddress;
    }

    public void changePasswordHash(String passwordHash) {
        // 비밀번호는 항상 해시값 형태로만 교체한다.
        this.passwordHash = passwordHash;
    }
}
