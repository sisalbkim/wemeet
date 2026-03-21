package com.kopo.wemeet.config;

import com.kopo.wemeet.entity.AppUser;
import com.kopo.wemeet.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthDataInitializer implements CommandLineRunner {
    // 앱 시작 시 데모 계정을 넣는 초기화 클래스다.

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthDataInitializer(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // 계정 테이블에 기본 사용자만 준비해 둔다.
        List<AppUser> seedUsers = List.of(
                new AppUser("user-123", "user123", "김철수", "user123@wemeet.local", passwordEncoder.encode("pass1234"), "FRIEND123", "서울특별시 중구 명동길 74"),
                new AppUser("friend-lee", "user456", "이영희", "user456@wemeet.local", passwordEncoder.encode("pass1234"), "FRIEND456", "서울특별시 성동구 성수동1가"),
                new AppUser("friend-park", "user789", "박민수", "user789@wemeet.local", passwordEncoder.encode("pass1234"), "FRIEND789", "서울특별시 마포구 공덕동")
        );

        for (AppUser user : seedUsers) {
            if (userRepository.findById(user.getId()).isEmpty()) {
                userRepository.save(user);
            }
        }
    }
}
