package com.kopo.wemeet.repository;

import com.kopo.wemeet.repository.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SearchHistoryRepository는 엔티티 조회와 저장에 필요한 Spring Data JPA 접근 메서드를 제공합니다.
 */
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    // 사용자별 히스토리 조회와 일괄 삭제에 필요한 메서드를 선언한다.

    List<SearchHistory> findAllByUserIdOrderBySearchedAtDesc(String userId);

    long deleteByUserId(String userId);

    long deleteByIdAndUserId(Long id, String userId);
}
