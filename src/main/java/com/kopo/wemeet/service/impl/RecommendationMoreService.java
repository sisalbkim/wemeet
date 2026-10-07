package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.PlaceDTO;
import com.kopo.wemeet.dto.RecommendationMode;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RecommendationMoreService {

    private final ApiNaverPlaceSearchService apiNaverPlaceSearchService;
    private final RecommendationTravelService travelService;

    private final ConcurrentHashMap<String, MoreRecommendationState> states =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> latestKeys =
            new ConcurrentHashMap<>();

    public void save(
            String requesterId,
            String key,
            List<RecommendationSupport.VenueEvaluation> remainingEvaluations,
            int nextStart,
            String category,
            String detailKeyword,
            RecommendationMode mode,
            RecommendationSupport.RoutePreference routePreference,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints,
            RecommendationSupport.GeoPoint midpointPoint,
            String anchorParticipantId,
            String searchOrigin,
            List<String> shownVenueKeys
    ) {
        latestKeys.put(requesterId, key);

        states.put(
                key,
                new MoreRecommendationState(
                        remainingEvaluations,
                        nextStart,
                        category,
                        detailKeyword,
                        mode,
                        routePreference,
                        participants,
                        participantPoints,
                        midpointPoint,
                        anchorParticipantId,
                        searchOrigin,
                        shownVenueKeys
                )
        );
    }

    public Optional<MoreRecommendationState> get(String key) {
        return Optional.ofNullable(states.get(key));
    }

    public Optional<String> getLatestKey(String requesterId) {
        return Optional.ofNullable(latestKeys.get(requesterId));
    }

    public MoreRecommendationState requireState(String key) {
        return get(key)
                .orElseThrow(() -> new IllegalStateException(
                        "추가 추천 정보를 찾을 수 없습니다."
                ));
    }
    public RecommendationSupport.VenueSelectionResult more(
            String requesterId,
            String key
    ) {
        if (!key.equals(latestKeys.get(requesterId))) {
            throw new IllegalArgumentException("현재 사용자의 추천 정보가 아닙니다.");
        }
        MoreRecommendationState state = requireState(key);

        List<RecommendationSupport.VenueEvaluation> remaining =
                new java.util.ArrayList<>(state.remainingEvaluations());
        // 합쳐진 후보 중 다시 3개 선정
        RecommendationSupport.VenueSelectionResult result =
                travelService.selectEvaluationsByMode(
                        remaining,
                        state.mode(),
                        state.routePreference()
                );

        List<String> updatedShownVenueKeys =
                new java.util.ArrayList<>(state.shownVenueKeys());

        result.selected().forEach(evaluation ->
                updatedShownVenueKeys.add(
                        evaluation.response().name() + "|" +
                                evaluation.response().latitude() + "|" +
                                evaluation.response().longitude()
                )
        );

        save(
                requesterId,
                key,
                result.remaining(),
                state.nextStart() + 5,
                state.category(),
                state.detailKeyword(),
                state.mode(),
                state.routePreference(),
                state.participants(),
                state.participantPoints(),
                state.midpointPoint(),
                state.anchorParticipantId(),
                state.searchOrigin(),
                updatedShownVenueKeys
        );

        return result;
    }

    public record MoreRecommendationState(
            List<RecommendationSupport.VenueEvaluation> remainingEvaluations,
            int nextStart,
            String category,
            String detailKeyword,
            RecommendationMode mode,
            RecommendationSupport.RoutePreference routePreference,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints,
            RecommendationSupport.GeoPoint midpointPoint,
            String anchorParticipantId,
            String searchOrigin,
            List<String> shownVenueKeys
    ) {
    }


}