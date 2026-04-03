package com.kopo.wemeet.repository;

import com.kopo.wemeet.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    // 사용자별 히스토리 조회와 일괄 삭제에 필요한 메서드를 선언한다.

    List<SearchHistory> findAllByUserIdOrderBySearchedAtDesc(String userId);

    long deleteByUserId(String userId);

    long deleteByIdAndUserId(Long id, String userId);
}
