package com.kopo.wemeet.dto;

import java.util.List;

public final class PlaceDTO {

    private PlaceDTO() {
    }

    public record PlaceSearchRequest(
            String originQuery,
            String tag,
            Integer display
    ) {
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
