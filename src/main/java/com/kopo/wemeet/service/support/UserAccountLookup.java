package com.kopo.wemeet.service.support;

import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * UserAccountLookup는 구현체가 지켜야 할 서비스 계층 계약을 정의합니다.
 */
@Component
@RequiredArgsConstructor
public class UserAccountLookup {
    // AppUser 엔티티를 화면/추천 로직에서 쓰는 읽기 전용 계정 DTO로 변환한다.

    private final AppUserRepository userRepository;

    public Optional<UserDTO.UserAccount> findById(String userId) {
        return userRepository.findById(userId).map(this::toUserAccount);
    }

    private UserDTO.UserAccount toUserAccount(AppUser user) {
        java.time.LocalDate joinedOn = user.getCreatedAt() == null
                ? java.time.LocalDate.now()
                : user.getCreatedAt().toLocalDate();
        return new UserDTO.UserAccount(
                user.getId(),
                user.getNickname(),
                user.getLoginId(),
                "",
                user.getFriendCode(),
                user.getBaseAddress(),
                joinedOn,
                false
        );
    }
}
