package com.kopo.wemeet.repository;

import com.kopo.wemeet.repository.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PasswordResetTokenRepository는 엔티티 조회와 저장에 필요한 Spring Data JPA 접근 메서드를 제공합니다.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    // 아직 사용되지 않았고 만료되지 않은 재설정 토큰만 조회할 때 사용한다.

    Optional<PasswordResetToken> findByTokenHashAndUsedFalseAndExpiresAtAfter(String tokenHash, LocalDateTime now);

    long deleteByUserId(String userId);
}
