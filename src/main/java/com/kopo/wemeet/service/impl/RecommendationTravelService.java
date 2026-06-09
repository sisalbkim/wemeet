package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.PlaceDTO;
import com.kopo.wemeet.dto.RecommendationDTO;
import com.kopo.wemeet.dto.RecommendationMode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RecommendationTravelService {
    // 이동시간 계산, 장소 평가, 최종 후보 선정 로직을 담당한다.

    private final ApiOpenRoutingService apiOpenRoutingService;
    private final ApiNaverPlaceSearchService apiNaverPlaceSearchService;
    private final ApiOdsayTransitRoutingService apiOdsayTransitRoutingService;
    private final ApiTmapWalkingRoutingService apiTmapWalkingRoutingService;
    private final RecommendationLocationService locationService;
    private final RecommendationParticipantService participantService;

    public RecommendationTravelService(
            ApiOpenRoutingService apiOpenRoutingService,
            ApiNaverPlaceSearchService apiNaverPlaceSearchService,
            ApiOdsayTransitRoutingService apiOdsayTransitRoutingService,
            ApiTmapWalkingRoutingService apiTmapWalkingRoutingService,
            RecommendationLocationService locationService,
            RecommendationParticipantService participantService
    ) {
        this.apiOpenRoutingService = apiOpenRoutingService;
        this.apiNaverPlaceSearchService = apiNaverPlaceSearchService;
        this.apiOdsayTransitRoutingService = apiOdsayTransitRoutingService;
        this.apiTmapWalkingRoutingService = apiTmapWalkingRoutingService;
        this.locationService = locationService;
        this.participantService = participantService;
    }

    public List<PlaceDTO.PlaceCandidateResponse> narrowToNearbyPlaces(List<PlaceDTO.PlaceCandidateResponse> places) {
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

    public RecommendationSupport.VenueEvaluation evaluatePlaceCandidate(
            PlaceDTO.PlaceSearchResponse placeSearch,
            PlaceDTO.PlaceCandidateResponse place,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints,
            RecommendationSupport.GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId
    ) {
        RecommendationSupport.TravelResolution travelResolution = resolveTravelMinutes(placeSearch, place, participants, participantPoints);
        List<RecommendationDTO.TravelTimeResponse> travelTimes = participants.stream()
                .map(participant -> new RecommendationDTO.TravelTimeResponse(
                        participant.id(),
                        participant.nickname(),
                        travelResolution.travelByUserId()
                                .getOrDefault(participant.id(), new RecommendationSupport.ParticipantRouteEstimate(place.durationMinutes(), List.of()))
                                .minutes(),
                        travelResolution.travelByUserId()
                                .getOrDefault(participant.id(), new RecommendationSupport.ParticipantRouteEstimate(place.durationMinutes(), List.of()))
                                .routePath(),
                        travelResolution.travelByUserId()
                                .getOrDefault(participant.id(), new RecommendationSupport.ParticipantRouteEstimate(place.durationMinutes(), List.of()))
                                .routeModes()
                ))
                .toList();

        int min = travelTimes.stream().mapToInt(RecommendationDTO.TravelTimeResponse::minutes).min().orElse(0);
        int max = travelTimes.stream().mapToInt(RecommendationDTO.TravelTimeResponse::minutes).max().orElse(0);
        int average = (int) Math.round(travelTimes.stream().mapToInt(RecommendationDTO.TravelTimeResponse::minutes).average().orElse(0));
        int fairnessGap = max - min;
        RecommendationSupport.GeoPoint venuePoint = new RecommendationSupport.GeoPoint(place.latitude(), place.longitude());

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
                locationService.extractArea(place.roadAddress(), place.address()),
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

        return new RecommendationSupport.VenueEvaluation(response, strategyScore, travelResolution.usedFallbackRouting());
    }

    public List<RecommendationSupport.VenueEvaluation> selectEvaluationsByMode(
            List<RecommendationSupport.VenueEvaluation> candidateEvaluations,
            RecommendationMode mode,
            RecommendationSupport.RoutePreference routePreference
    ) {
        if (candidateEvaluations.isEmpty()) {
            return List.of();
        }

        if (mode == RecommendationMode.RANDOM) {
            List<RecommendationSupport.VenueEvaluation> pool = candidateEvaluations.stream()
                    .sorted(buildEvaluationComparator(routePreference))
                    .limit(Math.min(5, candidateEvaluations.size()))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(pool);
            return pool.stream().limit(Math.min(3, pool.size())).toList();
        }

        List<RecommendationSupport.VenueEvaluation> sortedEvaluations = candidateEvaluations.stream()
                .sorted(buildEvaluationComparator(routePreference))
                .toList();

        List<RecommendationSupport.VenueEvaluation> selectedEvaluations = new ArrayList<>();
        LinkedHashSet<String> usedAreas = new LinkedHashSet<>();

        for (RecommendationSupport.VenueEvaluation evaluation : sortedEvaluations) {
            if (usedAreas.add(evaluation.response().area())) {
                selectedEvaluations.add(evaluation);
            }
            if (selectedEvaluations.size() == 3) {
                return selectedEvaluations;
            }
        }

        for (RecommendationSupport.VenueEvaluation evaluation : sortedEvaluations) {
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

    private RecommendationSupport.TravelResolution resolveTravelMinutes(
            PlaceDTO.PlaceSearchResponse placeSearch,
            PlaceDTO.PlaceCandidateResponse place,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints
    ) {
        RecommendationSupport.GeoPoint destination = new RecommendationSupport.GeoPoint(place.latitude(), place.longitude());
        if (participants.size() == 1) {
            return new RecommendationSupport.TravelResolution(
                    Map.of(participants.get(0).id(), buildRouteEstimate(
                            place.durationMinutes(),
                            locationService.toRecommendationRoutePath(place.routePath(), participantPoints.get(0), destination),
                            participantPoints.get(0),
                            destination
                    )),
                    false
            );
        }

        Map<String, RecommendationSupport.ParticipantRouteEstimate> routedTravel = new LinkedHashMap<>();
        for (int index = 0; index < participants.size(); index++) {
            RecommendationSupport.ParticipantProfile participant = participants.get(index);
            RecommendationSupport.GeoPoint origin = participantPoints.get(index);
            Optional<ApiNaverPlaceSearchService.RouteEstimate> travelRoute = Optional.ofNullable(
                    apiNaverPlaceSearchService.estimateTravelRoute(
                            participant.baseAddress(),
                            place.latitude(),
                            place.longitude()
                    )
            ).orElse(Optional.empty());
            if (travelRoute.isEmpty()) {
                return new RecommendationSupport.TravelResolution(buildHeuristicTravelEstimates(place, participants, participantPoints), true);
            }
            routedTravel.put(participant.id(), buildRouteEstimate(
                    travelRoute.get().durationMinutes(),
                    locationService.toRecommendationRoutePath(travelRoute.get().routePath(), origin, destination),
                    origin,
                    destination
            ));
        }

        for (RecommendationSupport.ParticipantProfile participant : participants) {
            if (participantService.normalizeAddressForCache(participant.baseAddress()).equals(participantService.normalizeAddressForCache(placeSearch.origin().address()))
                    || participantService.normalizeAddressForCache(participant.baseAddress()).equals(participantService.normalizeAddressForCache(placeSearch.origin().query()))) {
                RecommendationSupport.GeoPoint origin = participantPoints.get(participants.indexOf(participant));
                routedTravel.put(participant.id(), buildRouteEstimate(
                        place.durationMinutes(),
                        locationService.toRecommendationRoutePath(place.routePath(), origin, destination),
                        origin,
                        destination
                ));
            }
        }

        return new RecommendationSupport.TravelResolution(routedTravel, false);
    }

    private Map<String, RecommendationSupport.ParticipantRouteEstimate> buildHeuristicTravelEstimates(
            PlaceDTO.PlaceCandidateResponse place,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints
    ) {
        Map<String, RecommendationSupport.ParticipantRouteEstimate> heuristicTravel = new LinkedHashMap<>();
        RecommendationSupport.GeoPoint destination = new RecommendationSupport.GeoPoint(place.latitude(), place.longitude());
        for (int index = 0; index < participants.size(); index++) {
            RecommendationSupport.GeoPoint origin = participantPoints.get(index);
            heuristicTravel.put(
                    participants.get(index).id(),
                    buildRouteEstimate(
                            estimateTravelMinutesHeuristically(origin, destination),
                            locationService.straightRoutePath(origin, destination),
                            origin,
                            destination
                    )
            );
        }
        return heuristicTravel;
    }

    private RecommendationSupport.ParticipantRouteEstimate buildRouteEstimate(
            int carMinutes,
            List<RecommendationDTO.RoutePointResponse> carRoutePath,
            RecommendationSupport.GeoPoint origin,
            RecommendationSupport.GeoPoint destination
    ) {
        List<RecommendationDTO.RoutePointResponse> straightPath = locationService.straightRoutePath(origin, destination);
        Optional<ApiTmapWalkingRoutingService.WalkingRouteEstimate> walkingRoute = apiTmapWalkingRoutingService.estimateWalkingRoute(
                origin.latitude(),
                origin.longitude(),
                destination.latitude(),
                destination.longitude()
        );
        Optional<ApiOpenRoutingService.RouteResult> fallbackWalkingRoute = walkingRoute.isPresent()
                ? Optional.empty()
                : apiOpenRoutingService.route(
                        origin.latitude(),
                        origin.longitude(),
                        destination.latitude(),
                        destination.longitude(),
                        apiOpenRoutingServiceWalkingProfile()
                );
        Optional<ApiOdsayTransitRoutingService.TransitRouteEstimate> transitRoute = apiOdsayTransitRoutingService.estimateTransitRoute(
                origin.latitude(),
                origin.longitude(),
                destination.latitude(),
                destination.longitude()
        );
        int walkingMinutes = walkingRoute.map(ApiTmapWalkingRoutingService.WalkingRouteEstimate::minutes)
                .orElseGet(() -> fallbackWalkingRoute.map(ApiOpenRoutingService.RouteResult::minutes)
                        .orElseGet(() -> estimateWalkingMinutes(origin, destination)));
        List<RecommendationDTO.RoutePointResponse> walkingPath = walkingRoute
                .map(ApiTmapWalkingRoutingService.WalkingRouteEstimate::routePath)
                .orElseGet(() -> fallbackWalkingRoute
                        .map(ApiOpenRoutingService.RouteResult::path)
                        .map(path -> locationService.toRecommendationRoutePathFromCoordinates(path, origin, destination))
                        .orElse(straightPath));

        List<RecommendationDTO.RouteModeResponse> routeModes = new ArrayList<>();
        routeModes.add(new RecommendationDTO.RouteModeResponse("car", "자동차", carMinutes, carRoutePath, true));
        routeModes.add(new RecommendationDTO.RouteModeResponse(
                "transit",
                "대중교통",
                transitRoute.map(ApiOdsayTransitRoutingService.TransitRouteEstimate::minutes).orElse(0),
                transitRoute.map(ApiOdsayTransitRoutingService.TransitRouteEstimate::routePath).orElse(List.of()),
                transitRoute.isPresent()
        ));
        routeModes.add(new RecommendationDTO.RouteModeResponse("walk", "걷기", walkingMinutes, walkingPath, true));

        return new RecommendationSupport.ParticipantRouteEstimate(carMinutes, carRoutePath, routeModes);
    }

    private int estimateTravelMinutesHeuristically(
            RecommendationSupport.GeoPoint origin,
            RecommendationSupport.GeoPoint destination
    ) {
        double distanceKm = locationService.distanceKilometers(origin, destination);
        return Math.max(3, (int) Math.round(distanceKm * 4.8d + 2d));
    }

    private int estimateWalkingMinutes(
            RecommendationSupport.GeoPoint origin,
            RecommendationSupport.GeoPoint destination
    ) {
        double distanceKm = locationService.distanceKilometers(origin, destination);
        return Math.max(1, (int) Math.round((distanceKm / 4.5d) * 60d));
    }

    private Comparator<RecommendationSupport.VenueEvaluation> buildEvaluationComparator(
            RecommendationSupport.RoutePreference routePreference
    ) {
        return Comparator
                .comparingInt((RecommendationSupport.VenueEvaluation evaluation) -> preferredRouteMissingPenalty(evaluation, routePreference))
                .thenComparingInt(evaluation -> preferredRouteAverageMinutes(evaluation, routePreference))
                .thenComparingDouble(RecommendationSupport.VenueEvaluation::strategyScore)
                .thenComparingInt(evaluation -> evaluation.response().fairnessGap())
                .thenComparingInt(evaluation -> evaluation.response().averageMinutes())
                .thenComparing(evaluation -> evaluation.response().name());
    }

    private int preferredRouteMissingPenalty(
            RecommendationSupport.VenueEvaluation evaluation,
            RecommendationSupport.RoutePreference routePreference
    ) {
        if (routePreference == RecommendationSupport.RoutePreference.CAR) {
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

    private int preferredRouteAverageMinutes(
            RecommendationSupport.VenueEvaluation evaluation,
            RecommendationSupport.RoutePreference routePreference
    ) {
        if (routePreference == RecommendationSupport.RoutePreference.CAR) {
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
            RecommendationSupport.RoutePreference routePreference
    ) {
        return findRouteMode(routeModes, routePreference)
                .map(RecommendationDTO.RouteModeResponse::available)
                .orElse(false);
    }

    private Optional<RecommendationDTO.RouteModeResponse> findRouteMode(
            List<RecommendationDTO.RouteModeResponse> routeModes,
            RecommendationSupport.RoutePreference routePreference
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
            RecommendationSupport.GeoPoint venuePoint,
            List<RecommendationDTO.TravelTimeResponse> travelTimes,
            int average,
            int fairnessGap,
            RecommendationSupport.GeoPoint midpointPoint,
            String anchorParticipantId
    ) {
        if (travelTimes.size() == 1) {
            return average * 3.2 + locationService.distance(venuePoint, midpointPoint) * 5200;
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

        double centerDistance = locationService.distance(venuePoint, midpointPoint);
        if (mode == RecommendationMode.CENTER) {
            return baseScore + centerDistance * 2800;
        }

        return baseScore + centerDistance * 1200;
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
        List<String> highlights = new ArrayList<>();
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String apiOpenRoutingServiceWalkingProfile() {
        return apiOpenRoutingService == null ? "foot" : apiOpenRoutingService.walkingProfile();
    }
}


