package com.kopo.wemeet.auth;

import com.kopo.wemeet.api.InMemoryWemeetStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthDataInitializer implements CommandLineRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InMemoryWemeetStore store;

    public AuthDataInitializer(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            InMemoryWemeetStore store
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.store = store;
    }

    @Override
    public void run(String... args) {
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

        userRepository.findAll().forEach(user ->
                store.syncUserSnapshot(user.getId(), user.getNickname(), user.getLoginId(), user.getFriendCode(), user.getBaseAddress())
        );
    }
}
