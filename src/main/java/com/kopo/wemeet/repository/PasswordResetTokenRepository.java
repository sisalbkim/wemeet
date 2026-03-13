package com.kopo.wemeet.repository;

import com.kopo.wemeet.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHashAndUsedFalseAndExpiresAtAfter(String tokenHash, LocalDateTime now);
}
