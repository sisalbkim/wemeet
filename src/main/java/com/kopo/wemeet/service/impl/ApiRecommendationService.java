package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.repository.WemeetDataStore;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IApiRecommendationService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ApiRecommendationService implements IApiRecommendationService {
    private static final int DEFAULT_SEARCH_DISPLAY = 5;

    private final WemeetDataStore store;
    private final OpenApiRoutingService openApiRoutingService;
    private final NaverPlaceSearchService naverPlaceSearchService;
    private final RecommendationCacheService recommendationCacheService;

    private final List<String> categories = List.of("맛집", "카페", "놀이", "문화", "운동", "기타");

    private final Map<String, GeoPoint> zoneCenters = Map.of(
            "중구", new GeoPoint(37.5636, 126.9866),
            "성수", new GeoPoint(37.5446, 127.0557),
            "공덕", new GeoPoint(37.5441, 126.9518),
            "홍대", new GeoPoint(37.5572, 126.9245),
            "여의도", new GeoPoint(37.5219, 126.9245),
            "용산", new GeoPoint(37.5299, 126.9658),
            "건대", new GeoPoint(37.5400, 127.0693),
            "왕십리", new GeoPoint(37.5611, 127.0373),
            "기본", new GeoPoint(37.5665, 126.9780)
    );

    public ApiRecommendationService(
            WemeetDataStore store,
            OpenApiRoutingService openApiRoutingService,
            NaverPlaceSearchService naverPlaceSearchService,
            RecommendationCacheService recommendationCacheService
    ) {
        this.store = store;
        this.openApiRoutingService = openApiRoutingService;
        this.naverPlaceSearchService = naverPlaceSearchService;
        this.recommendationCacheService = recommendationCacheService;
    }

    @Override
    public ApiDtos.CategoryResponse categories() {
        return new ApiDtos.CategoryResponse(categories);
    }

    public ApiDtos.RecommendationResponse recommend(
            String requesterId,
            ApiDtos.RecommendationRequest request,
            IApiAuthService authService
    ) {
        List<WemeetDataStore.UserAccount> participantAccounts = resolveParticipants(requesterId, request.participantIds());
        List<ParticipantProfile> participants = participantAccounts.stream()
                .map(participant -> new ParticipantProfile(
                        participant.id(),
                        participant.nickname(),
                        participant.baseAddress()
                ))
                .toList();
        List<ApiDtos.UserResponse> participantResponses = participantAccounts.stream()
                .map(authService::toUserResponse)
                .toList();

        return recommendInternal(requesterId, request, participants, participantResponses, true);
    }

    @Override
    public ApiDtos.RecommendationResponse recommendForGuest(
            ApiDtos.UserResponse guestUser,
            ApiDtos.RecommendationRequest request
    ) {
        List<ParticipantProfile> participants = List.of(
                new ParticipantProfile(
                        guestUser.id(),
                        guestUser.nickname(),
                        guestUser.baseAddress()
                )
        );

        return recommendInternal(guestUser.id(), request, participants, List.of(guestUser), false);
    }

    private ApiDtos.RecommendationResponse recommendInternal(
            String requesterId,
            ApiDtos.RecommendationRequest request,
            List<ParticipantProfile> participants,
            List<ApiDtos.UserResponse> participantResponses,
            boolean persistHistory
    ) {
        String category = normalizeCategory(request.category());
        RecommendationMode mode = RecommendationMode.from(request.mode());
        List<GeoPoint> participantPoints = participants.stream()
                .map(this::resolveParticipantPoint)
                .toList();
        GeoPoint midpointPoint = calculateMidpoint(participantPoints);
        String anchorParticipantId = resolveAnchorParticipantId(mode, requesterId, request.anchorParticipantId(), participants);
        String cacheKey = buildCacheKey(requesterId, category, participants) + ":" + mode.name() + ":" + anchorParticipantId;

        if (mode != RecommendationMode.RANDOM) {
            Optional<ApiDtos.RecommendationResponse> cached = recommendationCacheService.get(cacheKey);
            if (cached.isPresent()) {
                if (persistHistory) {
                    store.appendHistory(requesterId, cached.get().midpoint().district() + " " + category, category);
                }
                return cached.get();
            }
        }

        ApiDtos.RecommendationResponse response = recommendWithNaverPlaces(
                requesterId,
                category,
                mode,
                participants,
                participantResponses,
                participantPoints,
                midpointPoint,
                anchorParticipantId,
                persistHistory
        );

        if (mode != RecommendationMode.RANDOM) {
            recommendationCacheService.put(cacheKey, response);
        }
        return response;
    }

    private ApiDtos.RecommendationResponse recommendWithNaverPlaces(
            String requesterId,
            String category,
            RecommendationMode mode,
            List<ParticipantProfile> participants,
            List<ApiDtos.UserResponse> participantResponses,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            String anchorParticipantId,
            boolean persistHistory
    ) {
        SearchAnchor searchAnchor = resolveSearchAnchor(mode, participants, participantPoints, midpointPoint, anchorParticipantId);
        ApiDtos.PlaceSearchResponse placeSearch;
        try {
            placeSearch = naverPlaceSearchService.search(
                    new ApiDtos.PlaceSearchRequest(searchAnchor.query(), category, DEFAULT_SEARCH_DISPLAY)
            );
        } catch (ResponseStatusException exception) {
            return buildUnavailableRecommendation(
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

        List<ApiDtos.PlaceCandidateResponse> candidatePlaces = participants.size() == 1
                ? narrowToNearbyPlaces(placeSearch.places())
                : placeSearch.places();

        List<VenueEvaluation> evaluatedPlaces = candidatePlaces.stream()
                .map(place -> evaluatePlaceCandidate(
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
            return buildEmptyRecommendation(
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

        List<VenueEvaluation> selectedEvaluations = selectEvaluationsByMode(evaluatedPlaces, mode);
        List<ApiDtos.VenueResponse> venues = selectedEvaluations.stream()
                .map(VenueEvaluation::response)
                .toList();

        ApiDtos.VenueResponse bestVenue = venues.get(0);
        boolean usedFallbackRouting = selectedEvaluations.stream().anyMatch(VenueEvaluation::usedFallbackRouting);
        GeoPoint referencePoint = mode == RecommendationMode.ANCHOR
                ? resolveAnchorPoint(anchorParticipantId, participants, participantPoints)
                : midpointPoint;

        ApiDtos.MidpointResponse midpoint = new ApiDtos.MidpointResponse(
                extractArea(placeSearch.origin().address(), searchAnchor.query()),
                placeSearch.origin().name(),
                referencePoint.latitude(),
                referencePoint.longitude(),
                bestVenue.averageMinutes(),
                bestVenue.fairnessGap(),
                strategyNote(mode, participants.size(), usedFallbackRouting)
        );

        List<ApiDtos.MapPointResponse> mapPoints = buildMapPoints(
                participants,
                participantPoints,
                midpointPoint,
                venues,
                mode,
                anchorParticipantId
        );

        if (persistHistory) {
            store.appendHistory(requesterId, placeSearch.combinedQuery(), category);
        }

        return new ApiDtos.RecommendationResponse(
                category,
                participantResponses,
                midpoint,
                venues,
                mapPoints,
                calculationModeLabel(mode, usedFallbackRouting)
        );
    }

    private ApiDtos.RecommendationResponse buildEmptyRecommendation(
            String category,
            List<ApiDtos.UserResponse> participantResponses,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId,
            ApiDtos.PlaceSearchResponse placeSearch,
            SearchAnchor searchAnchor
    ) {
        GeoPoint referencePoint = mode == RecommendationMode.ANCHOR
                ? resolveAnchorPoint(anchorParticipantId, participants, participantPoints)
                : midpointPoint;

        return new ApiDtos.RecommendationResponse(
                category,
                participantResponses,
                new ApiDtos.MidpointResponse(
                        extractArea(placeSearch.origin().address(), searchAnchor.query()),
                        placeSearch.origin().name(),
                        referencePoint.latitude(),
                        referencePoint.longitude(),
                        0,
                        0,
                        "네이버 검색 결과가 없어 출발지 기준 정보만 표시합니다."
                ),
                List.of(),
                buildMapPoints(participants, participantPoints, midpointPoint, List.of(), mode, anchorParticipantId),
                calculationModeLabel(mode, false)
        );
    }

    private ApiDtos.RecommendationResponse buildUnavailableRecommendation(
            String category,
            List<ApiDtos.UserResponse> participantResponses,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId,
            SearchAnchor searchAnchor
    ) {
        GeoPoint referencePoint = mode == RecommendationMode.ANCHOR
                ? resolveAnchorPoint(anchorParticipantId, participants, participantPoints)
                : midpointPoint;

        return new ApiDtos.RecommendationResponse(
                category,
                participantResponses,
                new ApiDtos.MidpointResponse(
                        extractArea(searchAnchor.query(), searchAnchor.query()),
                        searchAnchor.query(),
                        referencePoint.latitude(),
                        referencePoint.longitude(),
                        0,
                        0,
                        "네이버 장소 검색을 완료하지 못해 출발지 기준 정보만 표시합니다."
                ),
                List.of(),
                buildMapPoints(participants, participantPoints, midpointPoint, List.of(), mode, anchorParticipantId),
                calculationModeLabel(mode, true)
        );
    }

    private VenueEvaluation evaluatePlaceCandidate(
            ApiDtos.PlaceSearchResponse placeSearch,
            ApiDtos.PlaceCandidateResponse place,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId
    ) {
        TravelResolution travelResolution = resolveTravelMinutes(placeSearch, place, participants, participantPoints);
        List<ApiDtos.TravelTimeResponse> travelTimes = participants.stream()
                .map(participant -> new ApiDtos.TravelTimeResponse(
                        participant.id(),
                        participant.nickname(),
                        travelResolution.travelMinutesByUserId().getOrDefault(participant.id(), place.durationMinutes())
                ))
                .toList();

        int min = travelTimes.stream().mapToInt(ApiDtos.TravelTimeResponse::minutes).min().orElse(0);
        int max = travelTimes.stream().mapToInt(ApiDtos.TravelTimeResponse::minutes).max().orElse(0);
        int average = (int) Math.round(travelTimes.stream().mapToInt(ApiDtos.TravelTimeResponse::minutes).average().orElse(0));
        int fairnessGap = max - min;
        GeoPoint venuePoint = new GeoPoint(place.latitude(), place.longitude());

        double strategyScore = calculateStrategyScore(
                mode,
                venuePoint,
                travelTimes,
                average,
                fairnessGap,
                midpointPoint,
                anchorParticipantId
        );

        ApiDtos.VenueResponse response = new ApiDtos.VenueResponse(
                place.name(),
                place.normalizedCategory(),
                extractArea(place.roadAddress(), place.address()),
                placeSearch.origin().name(),
                place.latitude(),
                place.longitude(),
                firstNonBlank(place.roadAddress(), place.address(), place.name()),
                firstNonBlank(place.telephone()),
                firstNonBlank(place.link()),
                buildReason(participants.size(), fairnessGap, average, travelResolution.usedFallbackRouting()),
                fairnessGap,
                average,
                buildPlaceHighlights(placeSearch, place, participants.size()),
                travelTimes
        );

        return new VenueEvaluation(response, strategyScore, travelResolution.usedFallbackRouting());
    }

    private TravelResolution resolveTravelMinutes(
            ApiDtos.PlaceSearchResponse placeSearch,
            ApiDtos.PlaceCandidateResponse place,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints
    ) {
        if (participants.size() == 1) {
            return new TravelResolution(Map.of(participants.get(0).id(), place.durationMinutes()), false);
        }

        Map<String, Integer> routedMinutes = new LinkedHashMap<>();
        for (ParticipantProfile participant : participants) {
            Optional<Integer> travelMinutes = Optional.ofNullable(
                    naverPlaceSearchService.estimateTravelMinutes(
                            participant.baseAddress(),
                            place.latitude(),
                            place.longitude()
                    )
            ).orElse(Optional.empty());
            if (travelMinutes.isEmpty()) {
                return new TravelResolution(buildHeuristicTravelMinutes(place, participants, participantPoints), true);
            }
            routedMinutes.put(participant.id(), travelMinutes.get());
        }

        for (ParticipantProfile participant : participants) {
            if (normalizeAddressForCache(participant.baseAddress()).equals(normalizeAddressForCache(placeSearch.origin().address()))
                    || normalizeAddressForCache(participant.baseAddress()).equals(normalizeAddressForCache(placeSearch.origin().query()))) {
                routedMinutes.put(participant.id(), place.durationMinutes());
            }
        }

        return new TravelResolution(routedMinutes, false);
    }

    private Map<String, Integer> buildHeuristicTravelMinutes(
            ApiDtos.PlaceCandidateResponse place,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints
    ) {
        Map<String, Integer> heuristicMinutes = new LinkedHashMap<>();
        GeoPoint destination = new GeoPoint(place.latitude(), place.longitude());
        for (int index = 0; index < participants.size(); index++) {
            heuristicMinutes.put(
                    participants.get(index).id(),
                    estimateTravelMinutesHeuristically(participantPoints.get(index), destination)
            );
        }
        return heuristicMinutes;
    }

    private int estimateTravelMinutesHeuristically(GeoPoint origin, GeoPoint destination) {
        double latKm = Math.abs(origin.latitude() - destination.latitude()) * 111d;
        double lonKm = Math.abs(origin.longitude() - destination.longitude()) * 88d;
        double distanceKm = Math.sqrt((latKm * latKm) + (lonKm * lonKm));
        return Math.max(3, (int) Math.round(distanceKm * 4.8d + 2d));
    }

    private SearchAnchor resolveSearchAnchor(
            RecommendationMode mode,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            String anchorParticipantId
    ) {
        if (participants.size() == 1) {
            return new SearchAnchor(participants.get(0).baseAddress(), participantPoints.get(0));
        }

        if (mode == RecommendationMode.ANCHOR) {
            for (int index = 0; index < participants.size(); index++) {
                if (participants.get(index).id().equals(anchorParticipantId)) {
                    return new SearchAnchor(participants.get(index).baseAddress(), participantPoints.get(index));
                }
            }
        }

        Optional<String> reverseGeocodedQuery = Optional.ofNullable(
                naverPlaceSearchService.reverseGeocodeArea(midpointPoint.latitude(), midpointPoint.longitude())
        ).orElse(Optional.empty());
        if (reverseGeocodedQuery.isPresent()) {
            return new SearchAnchor(reverseGeocodedQuery.get(), midpointPoint);
        }

        String commonDistrict = extractCommonDistrict(participants);
        if (!commonDistrict.isBlank()) {
            return new SearchAnchor(commonDistrict, midpointPoint);
        }

        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < participantPoints.size(); index++) {
            double currentDistance = distance(participantPoints.get(index), midpointPoint);
            if (currentDistance < nearestDistance) {
                nearestDistance = currentDistance;
                nearestIndex = index;
            }
        }
        return new SearchAnchor(extractSearchArea(participants.get(nearestIndex).baseAddress()), midpointPoint);
    }

    private String extractCommonDistrict(List<ParticipantProfile> participants) {
        LinkedHashSet<String> districts = participants.stream()
                .map(ParticipantProfile::baseAddress)
                .map(this::extractAdministrativeDistrict)
                .filter(district -> !district.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (districts.size() == 1) {
            return districts.iterator().next();
        }
        return "";
    }

    private String extractSearchArea(String address) {
        if (address == null || address.isBlank()) {
            return "서울";
        }
        String district = extractAdministrativeDistrict(address);
        if (!district.isBlank()) {
            return district;
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length >= 2) {
            return tokens[0] + " " + tokens[1];
        }
        return tokens[0];
    }

    private String extractAdministrativeDistrict(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length >= 2) {
            return tokens[0] + " " + tokens[1];
        }
        return tokens[0];
    }

    private List<WemeetDataStore.UserAccount> resolveParticipants(
            String requesterId,
            List<String> participantIds
    ) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        uniqueIds.add(requesterId);
        if (participantIds != null) {
            uniqueIds.addAll(participantIds);
        }

        return uniqueIds.stream()
                .map(id -> store.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Participant not found: " + id)))
                .toList();
    }

    private List<ApiDtos.MapPointResponse> buildMapPoints(
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            List<ApiDtos.VenueResponse> venues,
            RecommendationMode mode,
            String anchorParticipantId
    ) {
        List<ApiDtos.MapPointResponse> mapPoints = new ArrayList<>();
        boolean singleParticipant = participants.size() == 1;

        for (int index = 0; index < participants.size(); index++) {
            ParticipantProfile participant = participants.get(index);
            GeoPoint point = participantPoints.get(index);
            mapPoints.add(new ApiDtos.MapPointResponse(
                    participant.id(),
                    participant.nickname(),
                    participant.baseAddress(),
                    point.latitude(),
                    point.longitude(),
                    participant.id().equals(anchorParticipantId) && mode == RecommendationMode.ANCHOR ? "anchor" : "participant",
                    false
            ));
        }

        if (!singleParticipant && (mode == RecommendationMode.CENTER || mode == RecommendationMode.RANDOM)) {
            mapPoints.add(new ApiDtos.MapPointResponse(
                    "midpoint",
                    "참가자 중심점",
                    "참가자 좌표 평균 중심",
                    midpointPoint.latitude(),
                    midpointPoint.longitude(),
                    "midpoint",
                    false
            ));
        }

        for (int index = 0; index < venues.size(); index++) {
            ApiDtos.VenueResponse venue = venues.get(index);
            mapPoints.add(new ApiDtos.MapPointResponse(
                    "venue-" + index,
                    venue.name(),
                    venue.description(),
                    venue.latitude(),
                    venue.longitude(),
                    "venue",
                    index == 0
            ));
        }

        return mapPoints;
    }

    private GeoPoint resolveParticipantPoint(ParticipantProfile participant) {
        return openApiRoutingService.geocodeAddress(participant.baseAddress())
                .map(point -> new GeoPoint(point.latitude(), point.longitude()))
                .orElseGet(() -> zoneCenters.getOrDefault(resolveZone(participant.baseAddress()), zoneCenters.get("기본")));
    }

    private GeoPoint calculateMidpoint(List<GeoPoint> participantPoints) {
        double averageLatitude = participantPoints.stream()
                .mapToDouble(GeoPoint::latitude)
                .average()
                .orElse(zoneCenters.get("기본").latitude());
        double averageLongitude = participantPoints.stream()
                .mapToDouble(GeoPoint::longitude)
                .average()
                .orElse(zoneCenters.get("기본").longitude());
        return new GeoPoint(averageLatitude, averageLongitude);
    }

    private List<VenueEvaluation> selectEvaluationsByMode(
            List<VenueEvaluation> candidateEvaluations,
            RecommendationMode mode
    ) {
        if (candidateEvaluations.isEmpty()) {
            return List.of();
        }

        if (mode == RecommendationMode.RANDOM) {
            List<VenueEvaluation> pool = candidateEvaluations.stream()
                    .sorted(Comparator.comparingDouble(VenueEvaluation::strategyScore))
                    .limit(Math.min(5, candidateEvaluations.size()))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(pool);
            return pool.stream().limit(Math.min(3, pool.size())).toList();
        }

        List<VenueEvaluation> sortedEvaluations = candidateEvaluations.stream()
                .sorted(Comparator
                        .comparingDouble(VenueEvaluation::strategyScore)
                        .thenComparingInt(evaluation -> evaluation.response().fairnessGap())
                        .thenComparingInt(evaluation -> evaluation.response().averageMinutes())
                        .thenComparing(evaluation -> evaluation.response().name()))
                .toList();

        List<VenueEvaluation> selectedEvaluations = new ArrayList<>();
        LinkedHashSet<String> usedAreas = new LinkedHashSet<>();

        for (VenueEvaluation evaluation : sortedEvaluations) {
            if (usedAreas.add(evaluation.response().area())) {
                selectedEvaluations.add(evaluation);
            }
            if (selectedEvaluations.size() == 3) {
                return selectedEvaluations;
            }
        }

        for (VenueEvaluation evaluation : sortedEvaluations) {
            if (selectedEvaluations.contains(evaluation)) {
                continue;
            }
            selectedEvaluations.add(evaluation);
            if (selectedEvaluations.size() == 3) {
                break;
            }
        }

        return selectedEvaluations;
    }

    private double calculateStrategyScore(
            RecommendationMode mode,
            GeoPoint venuePoint,
            List<ApiDtos.TravelTimeResponse> travelTimes,
            int average,
            int fairnessGap,
            GeoPoint midpointPoint,
            String anchorParticipantId
    ) {
        if (travelTimes.size() == 1) {
            return average * 3.2 + distance(venuePoint, midpointPoint) * 5200;
        }

        double baseScore = average + fairnessGap * 1.8;

        if (mode == RecommendationMode.ANCHOR) {
            int anchorMinutes = travelTimes.stream()
                    .filter(time -> time.participantId().equals(anchorParticipantId))
                    .mapToInt(ApiDtos.TravelTimeResponse::minutes)
                    .findFirst()
                    .orElse(average);
            return anchorMinutes * 2.4 + average * 0.8 + fairnessGap * 1.5;
        }

        double centerDistance = distance(venuePoint, midpointPoint);
        if (mode == RecommendationMode.CENTER) {
            return baseScore + centerDistance * 2800;
        }

        return baseScore + centerDistance * 1200;
    }

    private String resolveAnchorParticipantId(
            RecommendationMode mode,
            String requesterId,
            String requestedAnchorId,
            List<ParticipantProfile> participants
    ) {
        if (mode != RecommendationMode.ANCHOR) {
            return requesterId;
        }

        if (requestedAnchorId == null || requestedAnchorId.isBlank()) {
            return requesterId;
        }

        boolean exists = participants.stream().anyMatch(participant -> participant.id().equals(requestedAnchorId));
        return exists ? requestedAnchorId : requesterId;
    }

    private GeoPoint resolveAnchorPoint(
            String anchorParticipantId,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints
    ) {
        for (int index = 0; index < participants.size(); index++) {
            if (participants.get(index).id().equals(anchorParticipantId)) {
                return participantPoints.get(index);
            }
        }
        return participantPoints.get(0);
    }

    private String calculationModeLabel(RecommendationMode mode, boolean usedFallbackRouting) {
        return mode.label() + " · " + (usedFallbackRouting
                ? "Naver Local Search + 보조 이동시간 추정"
                : "Naver Local Search + Directions 5");
    }

    private String strategyNote(RecommendationMode mode, int participantCount, boolean usedFallbackRouting) {
        String routingNote = usedFallbackRouting
                ? "일부 후보는 네이버 길찾기 대신 좌표 기반 보조 추정값으로 계산했습니다."
                : "네이버 지역검색과 Directions 5 기준으로 이동시간을 계산했습니다.";

        return switch (mode) {
            case CENTER -> participantCount == 1
                    ? "1인 검색은 출발지 주변 실제 네이버 장소검색 결과를 우선 보여줍니다. " + routingNote
                    : "참가자 중심 좌표를 기준으로 네이버 실제 장소를 찾고 전체 이동시간을 다시 정렬했습니다. " + routingNote;
            case ANCHOR -> "기준 인물 주변 네이버 실제 장소를 먼저 찾고, 기준 인물 이동 부담을 더 크게 반영했습니다. " + routingNote;
            case RANDOM -> "중심점 근처 네이버 실제 장소 후보군 안에서 랜덤하게 보여줍니다. " + routingNote;
        };
    }

    private String buildReason(int participantCount, int fairnessGap, int averageMinutes, boolean usedFallbackRouting) {
        if (participantCount == 1) {
            return "입력한 출발지 주변 실제 검색 결과를 거리와 이동시간 기준으로 정렬했습니다.";
        }
        if (fairnessGap <= 8) {
            return usedFallbackRouting
                    ? "후보별 이동시간 차이가 비교적 작고, 일부 구간은 보조 추정값을 사용했습니다."
                    : "선택한 인원 기준으로 이동시간 차이가 비교적 작습니다.";
        }
        return "후보는 실제 검색 결과지만 일부 인원은 약 " + averageMinutes + "분 이상 이동할 수 있습니다.";
    }

    private List<String> buildPlaceHighlights(
            ApiDtos.PlaceSearchResponse placeSearch,
            ApiDtos.PlaceCandidateResponse place,
            int participantCount
    ) {
        List<String> highlights = new ArrayList<>();
        highlights.add("네이버 검색 기반 실제 장소");
        highlights.add(place.distanceMeters() + "m");
        if (participantCount == 1) {
            highlights.add(place.durationMinutes() + "분");
        }
        if (place.category() != null && !place.category().isBlank()) {
            highlights.add(place.category());
        }
        if (placeSearch.appliedQueryTerms() != null) {
            highlights.addAll(placeSearch.appliedQueryTerms());
        }
        return highlights.stream().filter(value -> value != null && !value.isBlank()).distinct().limit(4).toList();
    }

    private List<ApiDtos.PlaceCandidateResponse> narrowToNearbyPlaces(List<ApiDtos.PlaceCandidateResponse> places) {
        if (places == null || places.isEmpty()) {
            return List.of();
        }

        List<Integer> distanceThresholds = List.of(2000, 5000, 8000);
        for (Integer threshold : distanceThresholds) {
            List<ApiDtos.PlaceCandidateResponse> filtered = places.stream()
                    .filter(place -> place.distanceMeters() <= threshold)
                    .toList();
            if (!filtered.isEmpty()) {
                return filtered;
            }
        }
        return places;
    }

    private String extractArea(String primaryAddress, String fallbackAddress) {
        String address = firstNonBlank(primaryAddress, fallbackAddress, "");
        if (address.isBlank()) {
            return "근처";
        }
        String[] tokens = address.split("\\s+");
        if (tokens.length >= 2) {
            return tokens[1];
        }
        return tokens[0];
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String resolveZone(String baseAddress) {
        if (baseAddress == null) {
            return "기본";
        }
        if (baseAddress.contains("명동") || baseAddress.contains("중구")) {
            return "중구";
        }
        if (baseAddress.contains("성수") || baseAddress.contains("성동구")) {
            return "성수";
        }
        if (baseAddress.contains("공덕") || baseAddress.contains("마포구")) {
            return "공덕";
        }
        if (baseAddress.contains("홍대") || baseAddress.contains("연남")) {
            return "홍대";
        }
        if (baseAddress.contains("여의도")) {
            return "여의도";
        }
        if (baseAddress.contains("용산")) {
            return "용산";
        }
        if (baseAddress.contains("건대") || baseAddress.contains("광진구")) {
            return "건대";
        }
        if (baseAddress.contains("왕십리") || baseAddress.contains("행당동") || baseAddress.contains("상왕십리")) {
            return "왕십리";
        }
        return "기본";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank() || !categories.contains(category)) {
            return "맛집";
        }
        return category;
    }

    private String buildCacheKey(
            String requesterId,
            String category,
            List<ParticipantProfile> participants
    ) {
        String participantKey = participants.stream()
                .map(participant -> participant.id() + ":" + normalizeAddressForCache(participant.baseAddress()))
                .sorted()
                .collect(Collectors.joining(","));
        return requesterId + ":" + category + ":" + participantKey;
    }

    private String normalizeAddressForCache(String address) {
        if (address == null || address.isBlank()) {
            return "empty";
        }
        return address.replaceAll("\\s+", "").trim();
    }

    private double distance(GeoPoint first, GeoPoint second) {
        double latGap = first.latitude() - second.latitude();
        double lonGap = first.longitude() - second.longitude();
        return Math.sqrt(latGap * latGap + lonGap * lonGap);
    }

    private record VenueEvaluation(
            ApiDtos.VenueResponse response,
            double strategyScore,
            boolean usedFallbackRouting
    ) {
    }

    private record TravelResolution(
            Map<String, Integer> travelMinutesByUserId,
            boolean usedFallbackRouting
    ) {
    }

    private record SearchAnchor(
            String query,
            GeoPoint point
    ) {
    }

    private record GeoPoint(
            double latitude,
            double longitude
    ) {
    }

    private record ParticipantProfile(
            String id,
            String nickname,
            String baseAddress
    ) {
    }
}
