package com.kopo.wemeet.dto;

import java.time.LocalDateTime;

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
