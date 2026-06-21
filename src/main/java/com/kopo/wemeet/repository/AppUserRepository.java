package com.kopo.wemeet.repository;

import com.kopo.wemeet.repository.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * AppUserRepository는 엔티티 조회와 저장에 필요한 Spring Data JPA 접근 메서드를 제공합니다.
 */
public interface AppUserRepository extends JpaRepository<AppUser, String> {
    // 로그인 ID, 이메일, 친구코드 같은 유니크 값 조회를 위한 JPA Repository다.

    Optional<AppUser> findByLoginId(String loginId);

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByNicknameAndEmail(String nickname, String email);

    Optional<AppUser> findByLoginIdAndEmail(String loginId, String email);

    Optional<AppUser> findByNicknameAndLoginIdAndEmail(String nickname, String loginId, String email);

    Optional<AppUser> findByFriendCode(String friendCode);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByFriendCode(String friendCode);
}
