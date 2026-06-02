package com.kopo.wemeet.repository;

import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.repository.entity.AppUser;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class WemeetDataStore {
    // 공통 사용자 조회와 데모 데이터 보장을 담당하는 얇은 저장소 facade다.

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean demoFriendEnabled;

    public WemeetDataStore(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.demo-friend-enabled:false}") boolean demoFriendEnabled
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoFriendEnabled = demoFriendEnabled;
    }

    @PostConstruct
    @Transactional
    void ensureDemoFriendExists() {
        // 친구 코드 테스트를 바로 해볼 수 있도록 데모 친구 1명을 보장한다.
        if (!demoFriendEnabled) {
            return;
        }
        if (userRepository.existsByFriendCode("AAAAAA")) {
            return;
        }

        AppUser demoFriend = new AppUser(
                "user-demo-aaaaaa",
                "demo_friend_aaaaaa",
                "테스트 친구",
                "aaaaaa@wemeet.local",
                passwordEncoder.encode("Passw0rd!"),
                "AAAAAA",
                "서울특별시 강남구 테헤란로 212"
        );
        userRepository.save(demoFriend);
    }

    public Optional<UserDTO.UserAccount> findById(String userId) {
        return userRepository.findById(userId).map(this::toUserAccount);
    }

    private UserDTO.UserAccount toUserAccount(AppUser user) {
        return toUserAccount(user, false);
    }

    private UserDTO.UserAccount toUserAccount(AppUser user, boolean favorite) {
        // 영속 엔티티 전체 대신 외부에 노출해도 되는 읽기 전용 값만 남긴다.
        java.time.LocalDate joinedOn = user.getCreatedAt() == null ? java.time.LocalDate.now() : user.getCreatedAt().toLocalDate();
        return new UserDTO.UserAccount(
                user.getId(),
                user.getNickname(),
                user.getLoginId(),
                "",
                user.getFriendCode(),
                user.getBaseAddress(),
                joinedOn,
                favorite
        );
    }
}
