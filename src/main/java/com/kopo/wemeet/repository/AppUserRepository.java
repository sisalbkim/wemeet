package com.kopo.wemeet.repository;

import com.kopo.wemeet.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

    Optional<AppUser> findByLoginId(String loginId);

    Optional<AppUser> findByEmail(String email);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByFriendCode(String friendCode);
}
