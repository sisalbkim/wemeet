package com.kopo.wemeet.repository.entity;

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

import java.time.LocalDateTime;

/**
 * PasswordResetToken는 데이터베이스 테이블과 매핑되는 JPA 엔티티입니다.
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {
    // 비밀번호 재설정 요청 한 건을 나타내는 엔티티다.
    // 토큰 원문 대신 해시값과 만료 시각, 사용 여부를 저장한다.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(length = 80, nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(AppUser user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.used = false;
    }

    @PrePersist
    void onCreate() {
        // 토큰 생성 시각을 저장해 추후 이력 확인에 활용할 수 있다.
        createdAt = LocalDateTime.now();
    }

    public AppUser getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void markUsed() {
        // 한 번 사용한 재설정 토큰은 재사용되지 않도록 상태를 변경한다.
        this.used = true;
    }
}
