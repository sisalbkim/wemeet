package com.kopo.wemeet.repository;

import com.kopo.wemeet.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    List<SearchHistory> findAllByUserIdOrderBySearchedAtDesc(String userId);

    long deleteByUserId(String userId);

    long deleteByIdAndUserId(Long id, String userId);
}
