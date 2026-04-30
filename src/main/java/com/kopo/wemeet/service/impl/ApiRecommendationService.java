package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.*;

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
    // 참가자 목록과 추천 모드를 바탕으로 실제 장소 후보를 계산하는 핵심 서비스다.
    private static final int DEFAULT_SEARCH_DISPLAY = 5;
    private static final String RECOMMENDATION_CACHE_SCHEMA_VERSION = "odsay-route-v2";

    private final WemeetDataStore store;
    private final OpenApiRoutingService openApiRoutingService;
    private final NaverPlaceSearchService naverPlaceSearchService;
    private final RecommendationCacheService recommendationCacheService;
    private final OdsayTransitRoutingService odsayTransitRoutingService;

    // 내부 화면/API에서 공통으로 쓰는 추천 카테고리 목록이다.
    private final List<String> categories = List.of("맛집", "카페", "놀이", "문화", "운동", "기타");

    // 외부 지오코딩이 실패했을 때 사용할 서울 주요 권역의 대표 좌표다.
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
            RecommendationCacheService recommendationCacheService,
            OdsayTransitRoutingService odsayTransitRoutingService
    ) {
        this.store = store;
        this.openApiRoutingService = openApiRoutingService;
        this.naverPlaceSearchService = naverPlaceSearchService;
        this.recommendationCacheService = recommendationCacheService;
        this.odsayTransitRoutingService = odsayTransitRoutingService;
    }

    @Override
    public RecommendationDTO.CategoryResponse categories() {
        return new RecommendationDTO.CategoryResponse(categories);
    }

    public RecommendationDTO.RecommendationResponse recommend(
            String requesterId,
            RecommendationDTO.RecommendationRequest request,
            IApiAuthService authService
    ) {
        // 실제 사용자 추천은 참가자 계정 정보와 응답 DTO를 함께 준비해서 내부 계산기로 넘긴다.
        List<WemeetDataStore.UserAccount> participantAccounts = resolveParticipants(requesterId, request.participantIds());
        List<ParticipantProfile> participants = participantAccounts.stream()
                .map(participant -> new ParticipantProfile(
                        participant.id(),
                        participant.nickname(),
                        participant.baseAddress()
                ))
                .toList();
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
        List<ParticipantProfile> participants = List.of(
                new ParticipantProfile(
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
            List<ParticipantProfile> participants,
            List<UserDTO.UserResponse> participantResponses,
            boolean persistHistory
    ) {
        // 추천 흐름의 중심 메서드다: 입력 정규화 -> 좌표 계산 -> 캐시 확인 -> 실제 장소 검색 순서로 진행한다.
        String category = normalizeCategory(request.category());
        RecommendationMode mode = RecommendationMode.from(request.mode());
        RoutePreference routePreference = RoutePreference.from(request.routeMode());
        List<GeoPoint> participantPoints = participants.stream()
                .map(this::resolveParticipantPoint)
                .toList();
        GeoPoint midpointPoint = calculateMidpoint(participantPoints);
        String anchorParticipantId = resolveAnchorParticipantId(mode, requesterId, request.anchorParticipantId(), participants);
        String cacheKey = buildCacheKey(requesterId, category, participants) + ":" + mode.name() + ":" + anchorParticipantId + ":" + routePreference.name();

        if (mode != RecommendationMode.RANDOM) {
            Optional<RecommendationDTO.RecommendationResponse> cached = recommendationCacheService.get(cacheKey);
            if (cached.isPresent()) {
                if (persistHistory) {
                    store.appendHistory(requesterId, cached.get().midpoint().district() + " " + category, category);
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
            List<ParticipantProfile> participants,
            List<UserDTO.UserResponse> participantResponses,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            String anchorParticipantId,
            RoutePreference routePreference,
            boolean persistHistory
    ) {
        // 네이버 실제 장소 후보를 가져와 평가 점수를 매기고 최종 장소 1~3개를 선정한다.
        SearchAnchor searchAnchor = resolveSearchAnchor(mode, participants, participantPoints, midpointPoint, anchorParticipantId);
        PlaceDTO.PlaceSearchResponse placeSearch;
        try {
            placeSearch = naverPlaceSearchService.search(
                    new PlaceDTO.PlaceSearchRequest(searchAnchor.query(), category, DEFAULT_SEARCH_DISPLAY)
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

        List<PlaceDTO.PlaceCandidateResponse> candidatePlaces = participants.size() == 1
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

        List<VenueEvaluation> selectedEvaluations = selectEvaluationsByMode(evaluatedPlaces, mode, routePreference);
        List<RecommendationDTO.VenueResponse> venues = selectedEvaluations.stream()
                .map(VenueEvaluation::response)
                .toList();

        RecommendationDTO.VenueResponse bestVenue = venues.get(0);
        boolean usedFallbackRouting = selectedEvaluations.stream().anyMatch(VenueEvaluation::usedFallbackRouting);
        GeoPoint referencePoint = mode == RecommendationMode.ANCHOR
                ? resolveAnchorPoint(anchorParticipantId, participants, participantPoints)
                : midpointPoint;

        RecommendationDTO.MidpointResponse midpoint = new RecommendationDTO.MidpointResponse(
                extractArea(placeSearch.origin().address(), searchAnchor.query()),
                placeSearch.origin().name(),
                referencePoint.latitude(),
                referencePoint.longitude(),
                bestVenue.averageMinutes(),
                bestVenue.fairnessGap(),
                strategyNote(mode, participants.size(), usedFallbackRouting)
        );

        List<RecommendationDTO.MapPointResponse> mapPoints = buildMapPoints(
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

        return new RecommendationDTO.RecommendationResponse(
                category,
                participantResponses,
                midpoint,
                venues,
                mapPoints,
                calculationModeLabel(mode, usedFallbackRouting)
        );
    }

    private RecommendationDTO.RecommendationResponse buildEmptyRecommendation(
            String category,
            List<UserDTO.UserResponse> participantResponses,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId,
            PlaceDTO.PlaceSearchResponse placeSearch,
            SearchAnchor searchAnchor
    ) {
        // 검색은 성공했지만 후보가 없을 때도 지도 기준점과 설명 문구는 유지한다.
        GeoPoint referencePoint = mode == RecommendationMode.ANCHOR
                ? resolveAnchorPoint(anchorParticipantId, participants, participantPoints)
                : midpointPoint;

        return new RecommendationDTO.RecommendationResponse(
                category,
                participantResponses,
                new RecommendationDTO.MidpointResponse(
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

    private RecommendationDTO.RecommendationResponse buildUnavailableRecommendation(
            String category,
            List<UserDTO.UserResponse> participantResponses,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId,
            SearchAnchor searchAnchor
    ) {
        // 네이버 API 자체가 실패한 경우 최소한의 기준 정보만 가진 응답으로 내려간다.
        GeoPoint referencePoint = mode == RecommendationMode.ANCHOR
                ? resolveAnchorPoint(anchorParticipantId, participants, participantPoints)
                : midpointPoint;

        return new RecommendationDTO.RecommendationResponse(
                category,
                participantResponses,
                new RecommendationDTO.MidpointResponse(
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
            PlaceDTO.PlaceSearchResponse placeSearch,
            PlaceDTO.PlaceCandidateResponse place,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId
    ) {
        // 장소 하나를 참가자별 이동시간, 공평성, 전략 점수로 평가해 정렬 가능한 값으로 바꾼다.
        TravelResolution travelResolution = resolveTravelMinutes(placeSearch, place, participants, participantPoints);
        List<RecommendationDTO.TravelTimeResponse> travelTimes = participants.stream()
                .map(participant -> new RecommendationDTO.TravelTimeResponse(
                        participant.id(),
                        participant.nickname(),
                        travelResolution.travelByUserId()
                                .getOrDefault(participant.id(), new ParticipantRouteEstimate(place.durationMinutes(), List.of()))
                                .minutes(),
                        travelResolution.travelByUserId()
                                .getOrDefault(participant.id(), new ParticipantRouteEstimate(place.durationMinutes(), List.of()))
                                .routePath(),
                        travelResolution.travelByUserId()
                                .getOrDefault(participant.id(), new ParticipantRouteEstimate(place.durationMinutes(), List.of()))
                                .routeModes()
                ))
                .toList();

        int min = travelTimes.stream().mapToInt(RecommendationDTO.TravelTimeResponse::minutes).min().orElse(0);
        int max = travelTimes.stream().mapToInt(RecommendationDTO.TravelTimeResponse::minutes).max().orElse(0);
        int average = (int) Math.round(travelTimes.stream().mapToInt(RecommendationDTO.TravelTimeResponse::minutes).average().orElse(0));
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

        RecommendationDTO.VenueResponse response = new RecommendationDTO.VenueResponse(
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
            PlaceDTO.PlaceSearchResponse placeSearch,
            PlaceDTO.PlaceCandidateResponse place,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints
    ) {
        // 우선 네이버 길찾기 실측값을 시도하고, 실패하면 전체 참가자를 같은 휴리스틱 방식으로 계산한다.
        GeoPoint destination = new GeoPoint(place.latitude(), place.longitude());
        if (participants.size() == 1) {
            return new TravelResolution(
                    Map.of(participants.get(0).id(), buildRouteEstimate(
                            place.durationMinutes(),
                            toRecommendationRoutePath(place.routePath(), participantPoints.get(0), destination),
                            participantPoints.get(0),
                            destination
                    )),
                    false
            );
        }

        Map<String, ParticipantRouteEstimate> routedTravel = new LinkedHashMap<>();
        for (int index = 0; index < participants.size(); index++) {
            ParticipantProfile participant = participants.get(index);
            GeoPoint origin = participantPoints.get(index);
            Optional<NaverPlaceSearchService.RouteEstimate> travelRoute = Optional.ofNullable(
                    naverPlaceSearchService.estimateTravelRoute(
                            participant.baseAddress(),
                            place.latitude(),
                            place.longitude()
                    )
            ).orElse(Optional.empty());
            if (travelRoute.isEmpty()) {
                return new TravelResolution(buildHeuristicTravelEstimates(place, participants, participantPoints), true);
            }
            routedTravel.put(participant.id(), buildRouteEstimate(
                    travelRoute.get().durationMinutes(),
                    toRecommendationRoutePath(travelRoute.get().routePath(), origin, destination),
                    origin,
                    destination
            ));
        }

        for (ParticipantProfile participant : participants) {
            if (normalizeAddressForCache(participant.baseAddress()).equals(normalizeAddressForCache(placeSearch.origin().address()))
                    || normalizeAddressForCache(participant.baseAddress()).equals(normalizeAddressForCache(placeSearch.origin().query()))) {
                GeoPoint origin = participantPoints.get(participants.indexOf(participant));
                routedTravel.put(participant.id(), buildRouteEstimate(
                        place.durationMinutes(),
                        toRecommendationRoutePath(place.routePath(), origin, destination),
                        origin,
                        destination
                ));
            }
        }

        return new TravelResolution(routedTravel, false);
    }

    private Map<String, ParticipantRouteEstimate> buildHeuristicTravelEstimates(
            PlaceDTO.PlaceCandidateResponse place,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints
    ) {
        Map<String, ParticipantRouteEstimate> heuristicTravel = new LinkedHashMap<>();
        GeoPoint destination = new GeoPoint(place.latitude(), place.longitude());
        for (int index = 0; index < participants.size(); index++) {
            GeoPoint origin = participantPoints.get(index);
            heuristicTravel.put(
                    participants.get(index).id(),
                    buildRouteEstimate(
                            estimateTravelMinutesHeuristically(origin, destination),
                            straightRoutePath(origin, destination),
                            origin,
                            destination
                    )
            );
        }
        return heuristicTravel;
    }

    private ParticipantRouteEstimate buildRouteEstimate(
            int carMinutes,
            List<RecommendationDTO.RoutePointResponse> carRoutePath,
            GeoPoint origin,
            GeoPoint destination
    ) {
        List<RecommendationDTO.RoutePointResponse> straightPath = straightRoutePath(origin, destination);
        Optional<OpenApiRoutingService.RouteResult> walkingRoute = openApiRoutingService.route(
                origin.latitude(),
                origin.longitude(),
                destination.latitude(),
                destination.longitude(),
                openApiRoutingServiceWalkingProfile()
        );
        Optional<OdsayTransitRoutingService.TransitRouteEstimate> transitRoute = odsayTransitRoutingService.estimateTransitRoute(
                origin.latitude(),
                origin.longitude(),
                destination.latitude(),
                destination.longitude()
        );
        int walkingMinutes = walkingRoute.map(OpenApiRoutingService.RouteResult::minutes)
                .orElseGet(() -> estimateWalkingMinutes(origin, destination));
        List<RecommendationDTO.RoutePointResponse> walkingPath = walkingRoute
                .map(OpenApiRoutingService.RouteResult::path)
                .map(path -> toRecommendationRoutePathFromCoordinates(path, origin, destination))
                .orElse(straightPath);

        List<RecommendationDTO.RouteModeResponse> routeModes = new ArrayList<>();
        routeModes.add(new RecommendationDTO.RouteModeResponse("car", "자동차", carMinutes, carRoutePath, true));
        routeModes.add(new RecommendationDTO.RouteModeResponse(
                "transit",
                "대중교통",
                transitRoute.map(OdsayTransitRoutingService.TransitRouteEstimate::minutes).orElse(0),
                transitRoute.map(OdsayTransitRoutingService.TransitRouteEstimate::routePath).orElse(List.of()),
                transitRoute.isPresent()
        ));
        routeModes.add(new RecommendationDTO.RouteModeResponse("walk", "걷기", walkingMinutes, walkingPath, true));

        return new ParticipantRouteEstimate(carMinutes, carRoutePath, routeModes);
    }

    private List<RecommendationDTO.RoutePointResponse> toRecommendationRoutePath(
            List<PlaceDTO.PlaceRoutePointResponse> routePath,
            GeoPoint origin,
            GeoPoint destination
    ) {
        if (routePath == null || routePath.size() < 2) {
            return straightRoutePath(origin, destination);
        }
        return routePath.stream()
                .map(point -> new RecommendationDTO.RoutePointResponse(point.latitude(), point.longitude()))
                .toList();
    }

    private List<RecommendationDTO.RoutePointResponse> toRecommendationRoutePathFromCoordinates(
            List<OpenApiRoutingService.MapCoordinate> routePath,
            GeoPoint origin,
            GeoPoint destination
    ) {
        if (routePath == null || routePath.size() < 2) {
            return straightRoutePath(origin, destination);
        }
        return routePath.stream()
                .map(point -> new RecommendationDTO.RoutePointResponse(point.latitude(), point.longitude()))
                .toList();
    }

    private List<RecommendationDTO.RoutePointResponse> straightRoutePath(GeoPoint origin, GeoPoint destination) {
        return List.of(
                new RecommendationDTO.RoutePointResponse(origin.latitude(), origin.longitude()),
                new RecommendationDTO.RoutePointResponse(destination.latitude(), destination.longitude())
        );
    }

    private int estimateTravelMinutesHeuristically(GeoPoint origin, GeoPoint destination) {
        double distanceKm = distanceKilometers(origin, destination);
        return Math.max(3, (int) Math.round(distanceKm * 4.8d + 2d));
    }

    private int estimateWalkingMinutes(GeoPoint origin, GeoPoint destination) {
        double distanceKm = distanceKilometers(origin, destination);
        return Math.max(1, (int) Math.round((distanceKm / 4.5d) * 60d));
    }

    private double distanceKilometers(GeoPoint origin, GeoPoint destination) {
        double latKm = Math.abs(origin.latitude() - destination.latitude()) * 111d;
        double lonKm = Math.abs(origin.longitude() - destination.longitude()) * 88d;
        return Math.sqrt((latKm * latKm) + (lonKm * lonKm));
    }

    private SearchAnchor resolveSearchAnchor(
            RecommendationMode mode,
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            String anchorParticipantId
    ) {
        // 검색 시작점은 1인/ANCHOR/중심점 모드별로 다르게 정한다.
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
        // 요청자 본인은 항상 포함시키고, 중복 선택된 친구는 한 번만 계산한다.
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        uniqueIds.add(requesterId);
        if (participantIds != null) {
            uniqueIds.addAll(participantIds);
        }

        return uniqueIds.stream()
                .map(id -> store.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Participant not found: " + id)))
                .toList();
    }

    private List<RecommendationDTO.MapPointResponse> buildMapPoints(
            List<ParticipantProfile> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            List<RecommendationDTO.VenueResponse> venues,
            RecommendationMode mode,
            String anchorParticipantId
    ) {
        // 참가자, 중심점, 추천 장소를 지도에서 그대로 찍을 수 있는 포인트 목록으로 변환한다.
        List<RecommendationDTO.MapPointResponse> mapPoints = new ArrayList<>();
        boolean singleParticipant = participants.size() == 1;

        for (int index = 0; index < participants.size(); index++) {
            ParticipantProfile participant = participants.get(index);
            GeoPoint point = participantPoints.get(index);
            mapPoints.add(new RecommendationDTO.MapPointResponse(
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
            mapPoints.add(new RecommendationDTO.MapPointResponse(
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
            RecommendationDTO.VenueResponse venue = venues.get(index);
            mapPoints.add(new RecommendationDTO.MapPointResponse(
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
        // 주소 지오코딩이 실패하면 권역 대표 좌표로 대체해 추천 흐름이 끊기지 않게 한다.
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
            RecommendationMode mode,
            RoutePreference routePreference
    ) {
        // CENTER/ANCHOR는 점수순, RANDOM은 상위 후보군 안에서 섞어서 3개까지 고른다.
        if (candidateEvaluations.isEmpty()) {
            return List.of();
        }

        if (mode == RecommendationMode.RANDOM) {
            List<VenueEvaluation> pool = candidateEvaluations.stream()
                    .sorted(buildEvaluationComparator(routePreference))
                    .limit(Math.min(5, candidateEvaluations.size()))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(pool);
            return pool.stream().limit(Math.min(3, pool.size())).toList();
        }

        List<VenueEvaluation> sortedEvaluations = candidateEvaluations.stream()
                .sorted(buildEvaluationComparator(routePreference))
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

    private Comparator<VenueEvaluation> buildEvaluationComparator(RoutePreference routePreference) {
        return Comparator
                .comparingInt((VenueEvaluation evaluation) -> preferredRouteMissingPenalty(evaluation, routePreference))
                .thenComparingInt(evaluation -> preferredRouteAverageMinutes(evaluation, routePreference))
                .thenComparingDouble(VenueEvaluation::strategyScore)
                .thenComparingInt(evaluation -> evaluation.response().fairnessGap())
                .thenComparingInt(evaluation -> evaluation.response().averageMinutes())
                .thenComparing(evaluation -> evaluation.response().name());
    }

    private int preferredRouteMissingPenalty(VenueEvaluation evaluation, RoutePreference routePreference) {
        if (routePreference == RoutePreference.CAR) {
            return 0;
        }

        long availableCount = evaluation.response().travelTimes().stream()
                .filter(time -> routeModeAvailable(time.routeModes(), routePreference))
                .count();
        int participantCount = evaluation.response().travelTimes().size();
        if (availableCount == participantCount) {
            return 0;
        }
        if (availableCount > 0) {
            return 1;
        }
        return 2;
    }

    private int preferredRouteAverageMinutes(VenueEvaluation evaluation, RoutePreference routePreference) {
        if (routePreference == RoutePreference.CAR) {
            return evaluation.response().averageMinutes();
        }

        List<Integer> minutes = evaluation.response().travelTimes().stream()
                .map(time -> findRouteMode(time.routeModes(), routePreference))
                .flatMap(Optional::stream)
                .filter(RecommendationDTO.RouteModeResponse::available)
                .map(RecommendationDTO.RouteModeResponse::minutes)
                .toList();

        if (minutes.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        return (int) Math.round(minutes.stream().mapToInt(Integer::intValue).average().orElse(Integer.MAX_VALUE));
    }

    private boolean routeModeAvailable(
            List<RecommendationDTO.RouteModeResponse> routeModes,
            RoutePreference routePreference
    ) {
        return findRouteMode(routeModes, routePreference)
                .map(RecommendationDTO.RouteModeResponse::available)
                .orElse(false);
    }

    private Optional<RecommendationDTO.RouteModeResponse> findRouteMode(
            List<RecommendationDTO.RouteModeResponse> routeModes,
            RoutePreference routePreference
    ) {
        if (routeModes == null || routeModes.isEmpty()) {
            return Optional.empty();
        }

        return routeModes.stream()
                .filter(routeMode -> routeMode != null && routePreference.mode().equalsIgnoreCase(routeMode.mode()))
                .findFirst();
    }

    private double calculateStrategyScore(
            RecommendationMode mode,
            GeoPoint venuePoint,
            List<RecommendationDTO.TravelTimeResponse> travelTimes,
            int average,
            int fairnessGap,
            GeoPoint midpointPoint,
            String anchorParticipantId
    ) {
        // 전략별로 평균 이동시간, 공평성, 중심점 거리의 가중치를 다르게 준다.
        if (travelTimes.size() == 1) {
            return average * 3.2 + distance(venuePoint, midpointPoint) * 5200;
        }

        double baseScore = average + fairnessGap * 1.8;

        if (mode == RecommendationMode.ANCHOR) {
            int anchorMinutes = travelTimes.stream()
                    .filter(time -> time.participantId().equals(anchorParticipantId))
                    .mapToInt(RecommendationDTO.TravelTimeResponse::minutes)
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
        // 화면에 왜 이런 추천이 나왔는지 설명하는 문구를 만든다.
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
            PlaceDTO.PlaceSearchResponse placeSearch,
            PlaceDTO.PlaceCandidateResponse place,
            int participantCount
    ) {
        // 카드 UI에 노출할 짧은 강조 문구를 만든다.
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

    private List<PlaceDTO.PlaceCandidateResponse> narrowToNearbyPlaces(List<PlaceDTO.PlaceCandidateResponse> places) {
        if (places == null || places.isEmpty()) {
            return List.of();
        }

        List<Integer> distanceThresholds = List.of(2000, 5000, 8000);
        for (Integer threshold : distanceThresholds) {
            List<PlaceDTO.PlaceCandidateResponse> filtered = places.stream()
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
        return RECOMMENDATION_CACHE_SCHEMA_VERSION + ":" + requesterId + ":" + category + ":" + participantKey;
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
            RecommendationDTO.VenueResponse response,
            double strategyScore,
            boolean usedFallbackRouting
    ) {
        // 후보 장소 한 개를 정렬 가능한 내부 평가 결과로 감싼 record다.
    }

    private record TravelResolution(
            Map<String, ParticipantRouteEstimate> travelByUserId,
            boolean usedFallbackRouting
    ) {
        // 참가자별 이동시간과 fallback 사용 여부를 함께 전달한다.
    }

    private record ParticipantRouteEstimate(
            int minutes,
            List<RecommendationDTO.RoutePointResponse> routePath,
            List<RecommendationDTO.RouteModeResponse> routeModes
    ) {
        // 지도 렌더링에 필요한 참가자별 이동시간과 경로 좌표다.
        private ParticipantRouteEstimate(int minutes, List<RecommendationDTO.RoutePointResponse> routePath) {
            this(minutes, routePath, List.of(
                    new RecommendationDTO.RouteModeResponse("car", "자동차", minutes, routePath, true)
            ));
        }
    }

    private record SearchAnchor(
            String query,
            GeoPoint point
    ) {
        // 네이버 검색에 실제로 넣을 기준 질의어와 그 기준 좌표다.
    }

    private record GeoPoint(
            double latitude,
            double longitude
    ) {
        // 추천 계산 과정에서 공통으로 쓰는 단순 위경도 값이다.
    }

    private record ParticipantProfile(
            String id,
            String nickname,
            String baseAddress
    ) {
        // 추천 계산에 꼭 필요한 참가자 최소 정보만 담은 내부 모델이다.
    }

    private String openApiRoutingServiceWalkingProfile() {
        return openApiRoutingService == null ? "foot" : openApiRoutingService.walkingProfile();
    }

    private enum RoutePreference {
        CAR("car"),
        TRANSIT("transit"),
        WALK("walk");

        private final String mode;

        RoutePreference(String mode) {
            this.mode = mode;
        }

        public String mode() {
            return mode;
        }

        static RoutePreference from(String value) {
            if ("transit".equalsIgnoreCase(value)) {
                return TRANSIT;
            }
            if ("walk".equalsIgnoreCase(value)) {
                return WALK;
            }
            return CAR;
        }
    }
}
