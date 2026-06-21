package com.kopo.wemeet.dto;

import java.util.List;

/**
 * PlaceDTO는 계층 간 데이터 전달과 화면/API 응답 구성을 위한 DTO 묶음입니다.
 */
public final class PlaceDTO {
    // 장소 검색 결과와 경로 표시 데이터에 쓰는 DTO 모음이다.

    private PlaceDTO() {
    }

    public record PlaceSearchRequest(
            String originQuery,
            String tag,
            String detailKeyword,
            Integer display
    ) {
        public PlaceSearchRequest(String originQuery, String tag, Integer display) {
            this(originQuery, tag, "", display);
        }
    }

    public record PlaceSearchResponse(
            String combinedQuery,
            String requestedTag,
            String normalizedTag,
            List<String> appliedQueryTerms,
            List<String> observedCategories,
            PlaceSearchOriginResponse origin,
            List<PlaceCandidateResponse> places
    ) {
    }

    public record PlaceSearchOriginResponse(
            String query,
            String name,
            String address,
            double latitude,
            double longitude
    ) {
    }

    public record PlaceRoutePointResponse(
            double latitude,
            double longitude
    ) {
    }

    public record PlaceCandidateResponse(
            String name,
            String normalizedCategory,
            String category,
            String address,
            String roadAddress,
            String telephone,
            String link,
            double latitude,
            double longitude,
            int distanceMeters,
            int durationMinutes,
            List<PlaceRoutePointResponse> routePath
    ) {
    }

    public record PlaceTagResponse(
            String label,
            List<String> queryTerms
    ) {
    }

    public record PlaceTagCatalogResponse(
            List<PlaceTagResponse> tags
    ) {
    }
}
