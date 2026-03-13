package com.kopo.wemeet.repository;

import java.util.Optional;

public interface SessionTokenStore {

    void store(String token, String userId);

    Optional<String> findUserId(String token);
}
