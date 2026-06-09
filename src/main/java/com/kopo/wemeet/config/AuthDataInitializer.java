package com.kopo.wemeet.config;

import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.repository.entity.FriendRelation;
import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.FriendRelationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class AuthDataInitializer implements CommandLineRunner {
    // 앱 시작 시 데모 계정을 넣는 초기화 클래스다.

    private final AppUserRepository userRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean optionalDemoFriendEnabled;

    public AuthDataInitializer(
            AppUserRepository userRepository,
            FriendRelationRepository friendRelationRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.demo-friend-enabled:false}") boolean optionalDemoFriendEnabled
    ) {
        this.userRepository = userRepository;
        this.friendRelationRepository = friendRelationRepository;
        this.passwordEncoder = passwordEncoder;
        this.optionalDemoFriendEnabled = optionalDemoFriendEnabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // 계정 테이블에 기본 사용자와 데모 친구를 준비해 둔다.
        List<AppUser> seedUsers = List.of(
                new AppUser("user-123", "user123", "김철수", "user123@wemeet.local", passwordEncoder.encode("pass1234"), "FRIEND123", "서울특별시 중구 명동길 74"),
                new AppUser("friend-lee", "user456", "이영희", "user456@wemeet.local", passwordEncoder.encode("pass1234"), "FRIEND456", "서울특별시 성동구 성수동1가"),
                new AppUser("friend-park", "user789", "박민수", "user789@wemeet.local", passwordEncoder.encode("pass1234"), "FRIEND789", "서울특별시 마포구 공덕동"),
                new AppUser("friend-kim-test", "friendkim", "김철수", "friendkim@wemeet.local", passwordEncoder.encode("pass1234"), "FRIENDKIM", "서울특별시 종로구 세종대로 175"),
                new AppUser("friend-park-younghee", "parkyounghee", "박영희", "parkyounghee@wemeet.local", passwordEncoder.encode("pass1234"), "FRIENDYH", "서울특별시 서초구 서초대로 396"),
                new AppUser("friend-go-areum", "goareum", "고아름", "goareum@wemeet.local", passwordEncoder.encode("pass1234"), "FRIENDAREUM", "서울특별시 영등포구 여의대로 108"),
                new AppUser("friend-choi-minjun", "choiminjun", "최민준", "choiminjun@wemeet.local", passwordEncoder.encode("pass1234"), "FRIENDMJ", "서울특별시 송파구 올림픽로 300"),
                new AppUser("friend-jung-hana", "junghana", "정하나", "junghana@wemeet.local", passwordEncoder.encode("pass1234"), "FRIENDHN", "서울특별시 강남구 테헤란로 212"),
                new AppUser("friend-yoon-seo", "yoonseo", "윤서연", "yoonseo@wemeet.local", passwordEncoder.encode("pass1234"), "FRIENDYS", "서울특별시 용산구 한강대로 405"),
                new AppUser("friend-kang-doyun", "kangdoyun", "강도윤", "kangdoyun@wemeet.local", passwordEncoder.encode("pass1234"), "FRIENDDY", "서울특별시 구로구 디지털로 300"),
                new AppUser("friend-han-jisoo", "hanjisoo", "한지수", "hanjisoo@wemeet.local", passwordEncoder.encode("pass1234"), "FRIENDJS", "서울특별시 노원구 동일로 1414")
        );

        for (AppUser user : seedUsers) {
            if (userRepository.findById(user.getId()).isEmpty()) {
                userRepository.save(user);
            }
        }

        ensureDemoFriendRelations();
        ensureOptionalDemoFriend();
    }

    private void ensureOptionalDemoFriend() {
        if (!optionalDemoFriendEnabled || userRepository.existsByFriendCode("AAAAAA")) {
            return;
        }

        userRepository.save(new AppUser(
                "user-demo-aaaaaa",
                "demo_friend_aaaaaa",
                "테스트 친구",
                "aaaaaa@wemeet.local",
                passwordEncoder.encode("Passw0rd!"),
                "AAAAAA",
                "서울특별시 강남구 테헤란로 212"
        ));
    }

    private void ensureDemoFriendRelations() {
        AppUser mainUser = userRepository.findById("user-123").orElseThrow();
        List<String> friendIds = List.of(
                "friend-kim-test",
                "friend-park",
                "friend-lee",
                "friend-park-younghee",
                "friend-go-areum",
                "friend-choi-minjun",
                "friend-jung-hana",
                "friend-yoon-seo",
                "friend-kang-doyun",
                "friend-han-jisoo"
        );

        for (int i = 0; i < friendIds.size(); i++) {
            AppUser friend = userRepository.findById(friendIds.get(i)).orElseThrow();
            boolean favorite = i < 2;
            ensureFriendRelation(mainUser, friend, favorite);
            ensureFriendRelation(friend, mainUser, false);
        }
    }

    private void ensureFriendRelation(AppUser user, AppUser friend, boolean favorite) {
        if (friendRelationRepository.existsByUserIdAndFriendId(user.getId(), friend.getId())) {
            return;
        }

        FriendRelation relation = new FriendRelation(user, friend);
        relation.changeFavorite(favorite);
        friendRelationRepository.save(relation);
    }
}
