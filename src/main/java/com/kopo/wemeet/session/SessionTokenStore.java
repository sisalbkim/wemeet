package com.kopo.wemeet.session;

import java.util.Optional;

public interface SessionTokenStore {
    // 로그인 세션 토큰 저장소 계약이다. Redis나 메모리 구현으로 교체할 수 있다.

    void store(String token, String userId);

    Optional<String> findUserId(String token);
}
