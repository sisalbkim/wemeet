package com.kopo.wemeet.repository;

import java.util.Optional;

public interface SessionTokenStore {
    // 인증 토큰 저장소에 대한 추상화 인터페이스다.
    // 구현체를 바꾸면 메모리/Redis 방식을 서비스 코드 수정 없이 교체할 수 있다.

    void store(String token, String userId);

    Optional<String> findUserId(String token);
}
