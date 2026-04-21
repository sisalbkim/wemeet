package com.kopo.wemeet.dto;

public final class HistoryDTO {

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
}
