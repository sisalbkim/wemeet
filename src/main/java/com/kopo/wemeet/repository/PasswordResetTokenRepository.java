package com.kopo.wemeet.repository;

import com.kopo.wemeet.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    // 아직 사용되지 않았고 만료되지 않은 재설정 토큰만 조회할 때 사용한다.

    Optional<PasswordResetToken> findByTokenHashAndUsedFalseAndExpiresAtAfter(String tokenHash, LocalDateTime now);
}
