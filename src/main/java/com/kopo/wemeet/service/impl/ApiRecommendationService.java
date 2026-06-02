package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.PlaceDTO;
import com.kopo.wemeet.dto.RecommendationDTO;
import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.repository.WemeetDataStore;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IApiRecommendationService;
import com.kopo.wemeet.service.IHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class ApiRecommendationService implements IApiRecommendationService {
    // 참가자 목록과 추천 모드를 바탕으로 실제 장소 후보를 계산하는 오케스트레이션 서비스다.
    private static final int DEFAULT_SEARCH_DISPLAY = 5;
    private static final String RECOMMENDATION_CACHE_SCHEMA_VERSION = "tmap-walk-route-v3";

    private final RecommendationParticipantService participantService;
    private final RecommendationLocationService locationService;
    private final RecommendationTravelService travelService;
    private final RecommendationCacheService recommendationCacheService;
    private final NaverPlaceSearchService naverPlaceSearchService;
    private final RecommendationResponseFactory responseFactory;
    private final IHistoryService historyService;

    public ApiRecommendationService(
            RecommendationParticipantService participantService,
            RecommendationLocationService locationService,
            RecommendationTravelService travelService,
            RecommendationCacheService recommendationCacheService,
            NaverPlaceSearchService naverPlaceSearchService,
            RecommendationResponseFactory responseFactory,
            IHistoryService historyService
    ) {
        this.participantService = participantService;
        this.locationService = locationService;
        this.travelService = travelService;
        this.recommendationCacheService = recommendationCacheService;
        this.naverPlaceSearchService = naverPlaceSearchService;
        this.responseFactory = responseFactory;
        this.historyService = historyService;
    }

    @Override
    public RecommendationDTO.CategoryResponse categories() {
        return new RecommendationDTO.CategoryResponse(participantService.categories());
    }

    public RecommendationDTO.RecommendationResponse recommend(
            String requesterId,
            RecommendationDTO.RecommendationRequest request,
            IApiAuthService authService
    ) {
        List<UserDTO.UserAccount> participantAccounts = participantService.resolveParticipants(requesterId, request.participantIds());
        List<RecommendationSupport.ParticipantProfile> participants = participantService.toParticipantProfiles(participantAccounts);
        List<UserDTO.UserResponse> participantResponses = participantAccounts.stream()
                .map(authService::toUserResponse)
                .toList();

        return recommendInternal(requesterId, request, participants, participantResponses, true);
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

        return recommendInternal(guestUser.id(), request, participants, List.of(guestUser), false);
    }

    private RecommendationDTO.RecommendationResponse recommendInternal(
            String requesterId,
            RecommendationDTO.RecommendationRequest request,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<UserDTO.UserResponse> participantResponses,
            boolean persistHistory
    ) {
        String category = participantService.normalizeCategory(request.category());
        RecommendationMode mode = RecommendationMode.from(request.mode());
        RecommendationSupport.RoutePreference routePreference = RecommendationSupport.RoutePreference.from(request.routeMode());
        List<RecommendationSupport.GeoPoint> participantPoints = locationService.resolveParticipantPoints(participants);
        RecommendationSupport.GeoPoint midpointPoint = locationService.calculateMidpoint(participantPoints);
        String anchorParticipantId = participantService.resolveAnchorParticipantId(mode, requesterId, request.anchorParticipantId(), participants);
        String cacheKey = participantService.buildCacheKey(RECOMMENDATION_CACHE_SCHEMA_VERSION, requesterId, category, participants)
                + ":" + mode.name() + ":" + anchorParticipantId + ":" + routePreference.name();

        if (mode != RecommendationMode.RANDOM) {
            Optional<RecommendationDTO.RecommendationResponse> cached = recommendationCacheService.get(cacheKey);
            if (cached.isPresent()) {
                if (persistHistory) {
                    historyService.appendHistory(requesterId, cached.get().midpoint().district() + " " + category, category);
                }
                return cached.get();
            }
        }

        RecommendationDTO.RecommendationResponse response = recommendWithNaverPlaces(
                requesterId,
                category,
                mode,
                participants,
                participantResponses,
                participantPoints,
                midpointPoint,
                anchorParticipantId,
                routePreference,
                persistHistory
        );

        if (mode != RecommendationMode.RANDOM) {
            recommendationCacheService.put(cacheKey, response);
        }
        return response;
    }

    private RecommendationDTO.RecommendationResponse recommendWithNaverPlaces(
            String requesterId,
            String category,
            RecommendationMode mode,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<UserDTO.UserResponse> participantResponses,
            List<RecommendationSupport.GeoPoint> participantPoints,
            RecommendationSupport.GeoPoint midpointPoint,
            String anchorParticipantId,
            RecommendationSupport.RoutePreference routePreference,
            boolean persistHistory
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
            placeSearch = naverPlaceSearchService.search(
                    new PlaceDTO.PlaceSearchRequest(searchAnchor.query(), category, DEFAULT_SEARCH_DISPLAY)
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
                    searchAnchor
            );
        }

        List<PlaceDTO.PlaceCandidateResponse> candidatePlaces = participants.size() == 1
                ? travelService.narrowToNearbyPlaces(placeSearch.places())
                : placeSearch.places();

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
                    searchAnchor
            );
        }

        List<RecommendationSupport.VenueEvaluation> selectedEvaluations = travelService.selectEvaluationsByMode(
                evaluatedPlaces,
                mode,
                routePreference
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
                selectedEvaluations
        );

        if (persistHistory) {
            historyService.appendHistory(requesterId, placeSearch.combinedQuery(), category);
        }

        return response;
    }
}

