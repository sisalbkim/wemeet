package com.kopo.wemeet.dto;

import java.time.LocalDateTime;

/**
 * HistoryDTO는 계층 간 데이터 전달과 화면/API 응답 구성을 위한 DTO 묶음입니다.
 */
public final class HistoryDTO {
    // 검색 기록 API와 화면 응답에 쓰는 DTO 모음이다.

    private HistoryDTO() {
    }

    public record SearchHistoryResponse(
            String query,
            String category,
            String searchedAt
    ) {
    }

    public record SearchHistoryItem(
            Long id,
            String query,
            String category,
            String dateLabel
    ) {
    }

    public record SearchHistoryEntry(
            Long id,
            String query,
            String category,
            LocalDateTime searchedAt
    ) {
    }
}
