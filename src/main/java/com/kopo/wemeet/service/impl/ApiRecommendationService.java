package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.PlaceDTO;
import com.kopo.wemeet.dto.RecommendationDTO;
import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IApiRecommendationService;
import com.kopo.wemeet.service.IHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * ApiRecommendationService는 도메인 규칙과 외부 연동 흐름을 조합해 실제 비즈니스 처리를 수행합니다.
 */
@Service
@RequiredArgsConstructor
public class ApiRecommendationService implements IApiRecommendationService {
    // 참가자 목록과 추천 모드를 바탕으로 실제 장소 후보를 계산하는 오케스트레이션 서비스다.
    private static final int DEFAULT_SEARCH_DISPLAY = 9;
    private static final String RECOMMENDATION_CACHE_SCHEMA_VERSION = "tmap-walk-route-v4";

    private final RecommendationParticipantService participantService;
    private final RecommendationLocationService locationService;
    private final RecommendationTravelService travelService;
    private final RecommendationCacheService recommendationCacheService;
    private final ApiNaverPlaceSearchService apiNaverPlaceSearchService;
    private final RecommendationResponseFactory responseFactory;
    private final IHistoryService historyService;
    private final RecommendationMoreService recommendationMoreService;

    @Override
    public RecommendationDTO.CategoryResponse categories() {
        return new RecommendationDTO.CategoryResponse(participantService.categories());
    }

    public RecommendationDTO.RecommendationResponse recommend(
            String requesterId,
            RecommendationDTO.RecommendationRequest request,
            IApiAuthService authService
    ) {
        List<UserDTO.UserAccount> participantAccounts = participantService.resolveParticipants(requesterId, request.participantIds()).stream()
                .map(participant -> applyTemporaryRequesterOrigin(participant, requesterId, request.originAddress()))
                .toList();
        List<UserDTO.UserAccount> calculableAccounts = participantAccounts.stream()
                .filter(participant -> hasBaseAddress(participant.baseAddress()))
                .toList();
        List<UserDTO.UserResponse> excludedParticipantResponses = participantAccounts.stream()
                .filter(participant -> !hasBaseAddress(participant.baseAddress()))
                .map(authService::toUserResponse)
                .toList();
        if (calculableAccounts.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "at least one participant baseAddress is required");
        }

        List<RecommendationSupport.ParticipantProfile> participants = participantService.toParticipantProfiles(calculableAccounts);
        List<UserDTO.UserResponse> participantResponses = calculableAccounts.stream()
                .map(authService::toUserResponse)
                .toList();

        return recommendInternal(requesterId, request, participants, participantResponses, excludedParticipantResponses, true);
    }

    @Override
    public RecommendationDTO.RecommendationResponse recommendForGuest(
            UserDTO.UserResponse guestUser,
            RecommendationDTO.RecommendationRequest request
    ) {
        List<RecommendationSupport.ParticipantProfile> participants = List.of(
                new RecommendationSupport.ParticipantProfile(
                        guestUser.id(),
                        guestUser.nickname(),
                        guestUser.baseAddress()
                )
        );

        return recommendInternal(guestUser.id(), request, participants, List.of(guestUser), List.of(), false);
    }

    private RecommendationDTO.RecommendationResponse recommendInternal(
            String requesterId,
            RecommendationDTO.RecommendationRequest request,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<UserDTO.UserResponse> participantResponses,
            List<UserDTO.UserResponse> excludedParticipantResponses,
            boolean persistHistory
    ) {
        String category = participantService.normalizeCategory(request.category());
        String detailKeyword = normalizeOptionalText(request.detailKeyword());
        String searchKeyword = detailKeyword.isBlank() ? category : detailKeyword;
        RecommendationMode mode = RecommendationMode.from(request.mode());
        RecommendationSupport.RoutePreference routePreference = RecommendationSupport.RoutePreference.from(request.routeMode());
        List<RecommendationSupport.GeoPoint> participantPoints = locationService.resolveParticipantPoints(participants);
        RecommendationSupport.GeoPoint midpointPoint = locationService.calculateMidpoint(participantPoints);
        String anchorParticipantId = participantService.resolveAnchorParticipantId(mode, requesterId, request.anchorParticipantId(), participants);
        String excludedParticipantKey = excludedParticipantResponses.stream()
                .map(UserDTO.UserResponse::id)
                .sorted()
                .reduce("", (left, right) -> left.isBlank() ? right : left + "," + right);
        String cacheKey = participantService.buildCacheKey(RECOMMENDATION_CACHE_SCHEMA_VERSION, requesterId, category, participants)
                + ":" + searchKeyword + ":" + mode.name() + ":" + anchorParticipantId + ":" + routePreference.name() + ":" + excludedParticipantKey;

        if (false && mode != RecommendationMode.RANDOM) {
            Optional<RecommendationDTO.RecommendationResponse> cached = recommendationCacheService.get(cacheKey);
            if (cached.isPresent()) {
                if (persistHistory) {
                    historyService.appendHistory(requesterId, cached.get().midpoint().district() + " " + searchKeyword, category);
                }
                return cached.get();
            }
        }

        RecommendationDTO.RecommendationResponse response = recommendWithNaverPlaces(
                requesterId,
                category,
                detailKeyword,
                mode,
                participants,
                participantResponses,
                participantPoints,
                midpointPoint,
                anchorParticipantId,
                routePreference,
                excludedParticipantResponses,
                persistHistory,
                cacheKey
        );

        if (false && mode != RecommendationMode.RANDOM) {
            recommendationCacheService.put(cacheKey, response);
        }
        return response;
    }

    private RecommendationDTO.RecommendationResponse recommendWithNaverPlaces(
            String requesterId,
            String category,
            String detailKeyword,
            RecommendationMode mode,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<UserDTO.UserResponse> participantResponses,
            List<RecommendationSupport.GeoPoint> participantPoints,
            RecommendationSupport.GeoPoint midpointPoint,
            String anchorParticipantId,
            RecommendationSupport.RoutePreference routePreference,
            List<UserDTO.UserResponse> excludedParticipantResponses,
            boolean persistHistory,
            String cacheKey
    ) {
        RecommendationSupport.SearchAnchor searchAnchor = locationService.resolveSearchAnchor(
                mode,
                participants,
                participantPoints,
                midpointPoint,
                anchorParticipantId
        );
        PlaceDTO.PlaceSearchResponse placeSearch;
        try {
            placeSearch = apiNaverPlaceSearchService.search(
                    new PlaceDTO.PlaceSearchRequest(searchAnchor.query(), category, detailKeyword, DEFAULT_SEARCH_DISPLAY)
            );
        } catch (ResponseStatusException exception) {
            return responseFactory.buildUnavailableRecommendation(
                    category,
                    participantResponses,
                    participants,
                    participantPoints,
                    midpointPoint,
                    mode,
                    anchorParticipantId,
                    searchAnchor,
                    excludedParticipantResponses
            );
        }

        List<PlaceDTO.PlaceCandidateResponse> candidatePlaces = participants.size() == 1
                ? travelService.narrowToNearbyPlaces(placeSearch.places())
                : placeSearch.places();
        System.out.println("네이버 검색 결과: " + placeSearch.places().size());
        System.out.println("필터링 후 후보: " + candidatePlaces.size());

        List<RecommendationSupport.VenueEvaluation> evaluatedPlaces = candidatePlaces.stream()
                .map(place -> travelService.evaluatePlaceCandidate(
                        placeSearch,
                        place,
                        participants,
                        participantPoints,
                        midpointPoint,
                        mode,
                        anchorParticipantId
                ))
                .toList();

        if (evaluatedPlaces.isEmpty()) {
            return responseFactory.buildEmptyRecommendation(
                    category,
                    participantResponses,
                    participants,
                    participantPoints,
                    midpointPoint,
                    mode,
                    anchorParticipantId,
                    placeSearch,
                    searchAnchor,
                    excludedParticipantResponses
            );
        }

        RecommendationSupport.VenueSelectionResult selectionResult =
                travelService.selectEvaluationsByMode(
                        evaluatedPlaces,
                        mode,
                        routePreference
                );

        List<RecommendationSupport.VenueEvaluation> selectedEvaluations =
                selectionResult.selected();

        List<RecommendationSupport.VenueEvaluation> remainingEvaluations =
                selectionResult.remaining();
        recommendationMoreService.save(
                requesterId,
                cacheKey,
                remainingEvaluations,
                6,
                category,
                detailKeyword,
                mode,
                routePreference,
                participants,
                participantPoints,
                midpointPoint,
                anchorParticipantId,
                searchAnchor.query(),
                selectedEvaluations.stream()
                        .map(evaluation ->
                                evaluation.response().name() + "|" +
                                        evaluation.response().latitude() + "|" +
                                        evaluation.response().longitude()
                        )
                        .toList()
        );
        RecommendationDTO.RecommendationResponse response = responseFactory.buildRecommendation(
                category,
                participantResponses,
                participants,
                participantPoints,
                midpointPoint,
                mode,
                anchorParticipantId,
                placeSearch,
                searchAnchor,
                selectedEvaluations,
                excludedParticipantResponses
        );

        if (persistHistory) {
            historyService.appendHistory(requesterId, placeSearch.combinedQuery(), category);
        }

        return response;
    }

    private String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasBaseAddress(String baseAddress) {
        return baseAddress != null && !baseAddress.isBlank();
    }

    private UserDTO.UserAccount applyTemporaryRequesterOrigin(
            UserDTO.UserAccount participant,
            String requesterId,
            String originAddress
    ) {
        if (!participant.id().equals(requesterId) || hasBaseAddress(participant.baseAddress()) || !hasBaseAddress(originAddress)) {
            return participant;
        }
        return new UserDTO.UserAccount(
                participant.id(),
                participant.nickname(),
                participant.loginId(),
                participant.password(),
                participant.friendCode(),
                originAddress.trim(),
                participant.joinedOn(),
                participant.favorite()
        );
    }
}



