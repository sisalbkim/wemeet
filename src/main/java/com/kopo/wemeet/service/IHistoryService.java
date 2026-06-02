package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.HistoryDTO;

import java.util.List;

public interface IHistoryService {
    // 검색 기록 저장과 조회/삭제 로직을 담당한다.

    void appendHistory(String userId, String query, String category);

    List<HistoryDTO.SearchHistoryEntry> listHistory(String userId);

    void clearHistory(String userId);

    void removeHistory(String userId, Long historyId);
}

