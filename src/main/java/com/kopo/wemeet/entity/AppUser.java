package com.kopo.wemeet.entity;

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
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
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

    public void changeBaseAddress(String baseAddress) {
        this.baseAddress = baseAddress;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
