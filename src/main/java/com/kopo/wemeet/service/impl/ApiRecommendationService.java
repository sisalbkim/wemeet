package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.repository.InMemoryWemeetStore;
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

    private final InMemoryWemeetStore store;
    private final OpenApiRoutingService openApiRoutingService;
    private final RecommendationCacheService recommendationCacheService;

    private final List<String> categories = List.of("맛집", "카페", "놀이", "문화", "운동", "기타");

    private final List<CandidateVenue> candidateVenues = List.of(
            new CandidateVenue("몽탄", "맛집", "용산", "삼각지역", "서울 용산구 백범로99길 50", 37.5344, 126.9727, "삼각지권에서 실제로 많이 찾는 우대갈비 맛집입니다.", List.of("용산권 실존 맛집", "네이버 검색 노출 쉬움")),
            new CandidateVenue("을지로보석", "맛집", "을지로", "을지로3가역", "서울특별시 중구 저동2가 84-11 3층", 37.5669, 126.9913, "을지로권에서 검색되는 실제 식당을 기준으로 넣었습니다.", List.of("힙지로 실존 매장", "식사 후 2차 동선 좋음")),
            new CandidateVenue("청기와타운 왕십리역사점", "맛집", "왕십리", "왕십리역", "서울특별시 성동구 왕십리광장로 17 지상1층 C-04호", 37.5610, 127.0381, "왕십리역에서 실제로 검색되는 식당을 후보에 반영했습니다.", List.of("왕십리권 실존 맛집", "역사 내부 접근성")),
            new CandidateVenue("블루보틀 성수 카페", "카페", "성수", "뚝섬역", "서울 성동구 아차산로 7", 37.5477, 127.0459, "성수권에서 실제 검색되는 대표 카페입니다.", List.of("네이버 검색 가능", "성수 접근성 우수")),
            new CandidateVenue("어니언 성수", "카페", "성수", "성수역", "서울 성동구 아차산로9길 8", 37.5448, 127.0561, "성수 카페 거리에서 실제로 많이 찾는 매장입니다.", List.of("빵/커피 모두 가능", "성수동 실존 핫플")),
            new CandidateVenue("어질 인 왕십리역점", "카페", "왕십리", "왕십리역", "서울 성동구 마조로9길 18 1/2층", 37.5617, 127.0368, "왕십리역 인근에서 실제 검색되는 카페를 후보에 포함했습니다.", List.of("왕십리권 실존 카페", "한양대/왕십리 접근성")),
            new CandidateVenue("키랩 보드게임카페 건대본점", "놀이", "건대", "건대입구역", "서울 광진구 아차산로33길 49", 37.5413, 127.0685, "건대권에서 실제 검색되는 보드게임카페입니다.", List.of("실내 놀거리", "단체 이용 무난")),
            new CandidateVenue("서울이스케이프룸 홍대2호점", "놀이", "홍대", "홍대입구역", "서울특별시 마포구 서교동 410-9", 37.5568, 126.9230, "홍대권 실존 방탈출 매장 기준입니다.", List.of("홍대권 실존 매장", "활동형 모임 적합")),
            new CandidateVenue("국립현대미술관 서울", "문화", "종로", "안국역", "서울 종로구 삼청로 30", 37.5791, 126.9802, "실제 운영 중인 대표 문화 공간입니다.", List.of("전시 관람 가능", "삼청동 산책 연계")),
            new CandidateVenue("서울공예박물관", "문화", "종로", "안국역", "서울 종로구 율곡로3길 4", 37.5764, 126.9852, "안국권에서 실제 검색되는 문화 시설입니다.", List.of("실내 관람 가능", "한옥마을 연계")),
            new CandidateVenue("여의도한강공원", "운동", "여의도", "여의나루역", "서울특별시 영등포구 여의동로 330", 37.5263, 126.9336, "러닝과 산책 중심으로 많이 찾는 실제 장소입니다.", List.of("야외 활동 적합", "한강 접근성 우수")),
            new CandidateVenue("성수연방", "기타", "성수", "성수역", "서울 성동구 성수이로14길 14", 37.5446, 127.0553, "성수권에서 검색되는 복합문화공간입니다.", List.of("팝업/쇼핑 연계", "실존 복합 공간"))
    );

    private final Map<String, Map<String, Integer>> zoneTravelMinutes = Map.of(
            "중구", Map.of("을지로", 12, "성수", 21, "종로", 15, "여의도", 26, "용산", 16, "건대", 24, "홍대", 27, "왕십리", 19),
            "성수", Map.of("을지로", 21, "성수", 10, "종로", 25, "여의도", 34, "용산", 27, "건대", 14, "홍대", 29, "왕십리", 13),
            "공덕", Map.of("을지로", 20, "성수", 30, "종로", 18, "여의도", 14, "용산", 14, "건대", 31, "홍대", 18, "왕십리", 25),
            "홍대", Map.of("을지로", 25, "성수", 29, "종로", 22, "여의도", 17, "용산", 22, "건대", 24, "홍대", 9, "왕십리", 29),
            "여의도", Map.of("을지로", 24, "성수", 34, "종로", 24, "여의도", 8, "용산", 16, "건대", 36, "홍대", 18, "왕십리", 32),
            "용산", Map.of("을지로", 16, "성수", 24, "종로", 17, "여의도", 15, "용산", 7, "건대", 20, "홍대", 21, "왕십리", 18),
            "건대", Map.of("을지로", 24, "성수", 14, "종로", 28, "여의도", 36, "용산", 20, "건대", 8, "홍대", 25, "왕십리", 12),
            "왕십리", Map.of("을지로", 19, "성수", 13, "종로", 20, "여의도", 32, "용산", 18, "건대", 12, "홍대", 29, "왕십리", 7),
            "기본", Map.of("을지로", 24, "성수", 24, "종로", 24, "여의도", 24, "용산", 20, "건대", 24, "홍대", 24, "왕십리", 18)
    );

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
            InMemoryWemeetStore store,
            OpenApiRoutingService openApiRoutingService,
            RecommendationCacheService recommendationCacheService
    ) {
        this.store = store;
        this.openApiRoutingService = openApiRoutingService;
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
        String category = normalizeCategory(request.category());
        RecommendationMode mode = RecommendationMode.from(request.mode());
        List<InMemoryWemeetStore.UserAccount> participants = resolveParticipants(requesterId, request.participantIds());
        List<GeoPoint> participantPoints = participants.stream()
                .map(this::resolveParticipantPoint)
                .toList();
        GeoPoint midpointPoint = calculateMidpoint(participantPoints);
        String anchorParticipantId = resolveAnchorParticipantId(mode, requesterId, request.anchorParticipantId(), participants);
        String cacheKey = buildCacheKey(requesterId, category, participants) + ":" + mode.name() + ":" + anchorParticipantId;

        // 랜덤 추천은 호출할 때마다 결과가 달라져야 하므로 캐시하지 않는다.
        if (mode != RecommendationMode.RANDOM) {
            Optional<ApiDtos.RecommendationResponse> cached = recommendationCacheService.get(cacheKey);
            if (cached.isPresent()) {
                store.appendHistory(requesterId, cached.get().midpoint().district() + " " + category, category);
                return cached.get();
            }
        }

        List<VenueEvaluation> candidateEvaluations = candidateVenues.stream()
                .filter(candidate -> candidate.category().equals(category))
                .map(candidate -> evaluateCandidate(candidate, participants, midpointPoint, mode, anchorParticipantId))
                .toList();

        List<VenueEvaluation> venueEvaluations = selectEvaluationsByMode(candidateEvaluations, mode);

        List<ApiDtos.VenueResponse> venues = venueEvaluations.stream()
                .map(VenueEvaluation::response)
                .toList();

        ApiDtos.VenueResponse bestVenue = venues.get(0);
        boolean usedOpenApi = venueEvaluations.stream().anyMatch(VenueEvaluation::usedOpenApi);
        ApiDtos.MidpointResponse midpoint = new ApiDtos.MidpointResponse(
                bestVenue.area(),
                bestVenue.station(),
                mode == RecommendationMode.ANCHOR ? resolveAnchorPoint(anchorParticipantId, participants, participantPoints).latitude() : midpointPoint.latitude(),
                mode == RecommendationMode.ANCHOR ? resolveAnchorPoint(anchorParticipantId, participants, participantPoints).longitude() : midpointPoint.longitude(),
                bestVenue.averageMinutes(),
                bestVenue.fairnessGap(),
                strategyNote(mode, usedOpenApi)
        );

        List<ApiDtos.MapPointResponse> mapPoints = buildMapPoints(participants, participantPoints, midpointPoint, venues, mode, anchorParticipantId);
        String query = midpoint.district() + " " + category;
        store.appendHistory(requesterId, query, category);

        ApiDtos.RecommendationResponse response = new ApiDtos.RecommendationResponse(
                category,
                participants.stream().map(authService::toUserResponse).toList(),
                midpoint,
                venues,
                mapPoints,
                mode.label() + " · " + (usedOpenApi
                        ? "OpenStreetMap/OSRM REST API 기반 계산"
                        : "주소 키워드 기반 인메모리 계산")
        );
        if (mode != RecommendationMode.RANDOM) {
            recommendationCacheService.put(cacheKey, response);
        }
        return response;
    }

    private List<InMemoryWemeetStore.UserAccount> resolveParticipants(
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

    private VenueEvaluation evaluateCandidate(
            CandidateVenue candidate,
            List<InMemoryWemeetStore.UserAccount> participants,
            GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId
    ) {
        // 외부 경로 API가 실패하면 미리 정의한 지역별 평균 이동시간으로 계산을 이어간다.
        Optional<Map<String, Integer>> routedTravelMinutes = openApiRoutingService
                .estimateTravelMinutes(participants, candidate.fullAddress());

        Map<String, Integer> travelMinutesByUserId = routedTravelMinutes
                .orElseGet(() -> participants.stream()
                        .collect(Collectors.toMap(
                                InMemoryWemeetStore.UserAccount::id,
                                participant -> lookupTravelMinutes(resolveZone(participant.baseAddress()), candidate.area()),
                                (left, right) -> left,
                                LinkedHashMap::new
                        )));

        boolean usedOpenApi = routedTravelMinutes.isPresent();
        GeoPoint location = resolveCandidatePoint(candidate);

        List<ApiDtos.TravelTimeResponse> travelTimes = participants.stream()
                .map(participant -> new ApiDtos.TravelTimeResponse(
                        participant.id(),
                        participant.nickname(),
                        travelMinutesByUserId.getOrDefault(
                                participant.id(),
                                lookupTravelMinutes(resolveZone(participant.baseAddress()), candidate.area())
                        )
                ))
                .toList();

        int min = travelTimes.stream().mapToInt(ApiDtos.TravelTimeResponse::minutes).min().orElse(0);
        int max = travelTimes.stream().mapToInt(ApiDtos.TravelTimeResponse::minutes).max().orElse(0);
        int average = (int) Math.round(travelTimes.stream().mapToInt(ApiDtos.TravelTimeResponse::minutes).average().orElse(0));
        int fairnessGap = max - min;
        double strategyScore = calculateStrategyScore(
                mode,
                candidate,
                location,
                travelTimes,
                average,
                fairnessGap,
                midpointPoint,
                anchorParticipantId
        );
        String reason = fairnessGap <= 8
                ? "선택한 인원 기준으로 이동시간 차이가 비교적 작습니다."
                : "모임은 가능하지만 일부 인원은 더 오래 이동해야 합니다.";

        return new VenueEvaluation(
                new ApiDtos.VenueResponse(
                        candidate.name(),
                        candidate.category(),
                        candidate.area(),
                        candidate.station(),
                        location.latitude(),
                        location.longitude(),
                        candidate.description(),
                        reason,
                        fairnessGap,
                        average,
                        candidate.highlights(),
                        travelTimes
                ),
                usedOpenApi,
                strategyScore
        );
    }

    private List<ApiDtos.MapPointResponse> buildMapPoints(
            List<InMemoryWemeetStore.UserAccount> participants,
            List<GeoPoint> participantPoints,
            GeoPoint midpointPoint,
            List<ApiDtos.VenueResponse> venues,
            RecommendationMode mode,
            String anchorParticipantId
    ) {
        List<ApiDtos.MapPointResponse> mapPoints = new ArrayList<>();
        boolean singleParticipant = participants.size() == 1;

        for (int index = 0; index < participants.size(); index++) {
            InMemoryWemeetStore.UserAccount participant = participants.get(index);
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

        // 한 명만 참여한 경우에는 중심점이 출발지와 같아서 따로 표시하지 않는다.
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
            if (singleParticipant && index > 0) {
                continue;
            }
            ApiDtos.VenueResponse venue = venues.get(index);
            mapPoints.add(new ApiDtos.MapPointResponse(
                    "venue-" + index,
                    venue.name(),
                    venue.area() + " · " + venue.station(),
                    venue.latitude(),
                    venue.longitude(),
                    "venue",
                    index == 0
            ));
        }

        return mapPoints;
    }

    private int lookupTravelMinutes(String zone, String area) {
        return zoneTravelMinutes.getOrDefault(zone, zoneTravelMinutes.get("기본"))
                .getOrDefault(area, 24);
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
            List<InMemoryWemeetStore.UserAccount> participants
    ) {
        String participantKey = participants.stream()
                // 주소가 바뀌면 이전 추천 캐시를 재사용하지 않도록 캐시 키에 함께 포함한다.
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

    private GeoPoint resolveParticipantPoint(InMemoryWemeetStore.UserAccount participant) {
        return openApiRoutingService.geocodeAddress(participant.baseAddress())
                .map(point -> new GeoPoint(point.latitude(), point.longitude()))
                .orElseGet(() -> zoneCenters.getOrDefault(resolveZone(participant.baseAddress()), zoneCenters.get("기본")));
    }

    private GeoPoint resolveCandidatePoint(CandidateVenue candidate) {
        return openApiRoutingService.geocodeAddress(candidate.fullAddress())
                .map(point -> new GeoPoint(point.latitude(), point.longitude()))
                .orElseGet(() -> new GeoPoint(candidate.latitude(), candidate.longitude()));
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
        if (mode == RecommendationMode.RANDOM) {
            List<VenueEvaluation> pool = candidateEvaluations.stream()
                    .sorted(Comparator.comparingDouble(VenueEvaluation::strategyScore))
                    .limit(Math.min(5, candidateEvaluations.size()))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(pool);
            return pool.stream().limit(Math.min(3, pool.size())).toList();
        }

        return candidateEvaluations.stream()
                .sorted(Comparator
                        .comparingDouble(VenueEvaluation::strategyScore)
                        .thenComparingInt(evaluation -> evaluation.response().fairnessGap())
                        .thenComparingInt(evaluation -> evaluation.response().averageMinutes())
                        .thenComparing(evaluation -> evaluation.response().name()))
                .limit(3)
                .toList();
    }

    private double calculateStrategyScore(
            RecommendationMode mode,
            CandidateVenue candidate,
            GeoPoint venuePoint,
            List<ApiDtos.TravelTimeResponse> travelTimes,
            int average,
            int fairnessGap,
            GeoPoint midpointPoint,
            String anchorParticipantId
    ) {
        // 1인 모임은 공평성보다 출발지와의 거리와 이동시간을 더 크게 본다.
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
            List<InMemoryWemeetStore.UserAccount> participants
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
            List<InMemoryWemeetStore.UserAccount> participants,
            List<GeoPoint> participantPoints
    ) {
        for (int index = 0; index < participants.size(); index++) {
            if (participants.get(index).id().equals(anchorParticipantId)) {
                return participantPoints.get(index);
            }
        }
        return participantPoints.get(0);
    }

    private String strategyNote(RecommendationMode mode, boolean usedOpenApi) {
        String apiNote = usedOpenApi
                ? "Nominatim 지오코딩과 OSRM 경로 REST API를 호출해 이동시간을 계산했습니다."
                : "외부 API 실패 시 주소 기반 휴리스틱으로 계산합니다.";

        return switch (mode) {
            case CENTER -> "참가자가 한 명이면 출발지 기준 가까운 후보를, 여러 명이면 중심 좌표와 전체 이동시간을 함께 반영했습니다. " + apiNote;
            case ANCHOR -> "선택한 기준 인물의 이동 부담을 더 크게 반영했습니다. " + apiNote;
            case RANDOM -> "상식적인 후보군 안에서 랜덤하게 제안했습니다. " + apiNote;
        };
    }

    private double distance(GeoPoint first, GeoPoint second) {
        double latGap = first.latitude() - second.latitude();
        double lonGap = first.longitude() - second.longitude();
        return Math.sqrt(latGap * latGap + lonGap * lonGap);
    }

    private record CandidateVenue(
            String name,
            String category,
            String area,
            String station,
            String fullAddress,
            double latitude,
            double longitude,
            String description,
            List<String> highlights
    ) {
    }

    private record VenueEvaluation(
            ApiDtos.VenueResponse response,
            boolean usedOpenApi,
            double strategyScore
    ) {
    }

    private record GeoPoint(
            double latitude,
            double longitude
    ) {
    }
}
