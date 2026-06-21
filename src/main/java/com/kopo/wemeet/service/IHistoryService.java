package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.HistoryDTO;

import java.util.List;

/**
 * IHistoryService는 구현체가 지켜야 할 서비스 계층 계약을 정의합니다.
 */
public interface IHistoryService {
    // 검색 기록 저장과 조회/삭제 로직을 담당한다.

    void appendHistory(String userId, String query, String category);

    List<HistoryDTO.SearchHistoryEntry> listHistory(String userId);

    void clearHistory(String userId);

    void removeHistory(String userId, Long historyId);
}

