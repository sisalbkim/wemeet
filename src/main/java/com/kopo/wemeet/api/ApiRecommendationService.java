package com.kopo.wemeet.api;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ApiRecommendationService {

    private final InMemoryWemeetStore store;

    private final List<String> categories = List.of("맛집", "카페", "놀이", "문화", "운동", "기타");

    private final List<CandidateVenue> candidateVenues = List.of(
            new CandidateVenue("을지다락 서울숲점", "맛집", "을지로", "을지로3가역", "대화하기 좋은 식사 중심 장소입니다.", List.of("식사 후 카페 이동 쉬움", "약속 잡기 편한 위치")),
            new CandidateVenue("합정 푸드라운지", "맛집", "합정", "합정역", "식사 후 놀거리로 이어가기 좋은 복합 상권입니다.", List.of("저녁 모임 적합", "지하철 접근성 우수")),
            new CandidateVenue("연남 북카페", "카페", "연남", "홍대입구역", "오래 머무르기 좋은 넓은 좌석 중심 카페입니다.", List.of("디저트 다양", "대화/작업 모두 무난")),
            new CandidateVenue("서울숲 로스터리", "카페", "성수", "서울숲역", "산책과 함께 가볍게 만나기 좋은 카페입니다.", List.of("낮 시간 추천", "분위기 좋은 편")),
            new CandidateVenue("합정 보드게임 라운지", "놀이", "합정", "합정역", "보드게임과 식음료를 함께 즐길 수 있습니다.", List.of("초보자용 게임 많음", "주말 예약 추천")),
            new CandidateVenue("종로 방탈출 아지트", "놀이", "종로", "종각역", "활동적인 일정으로 이어가기 좋은 실내 놀거리입니다.", List.of("저녁 타임 다양", "인근 맛집 많음")),
            new CandidateVenue("서촌 전시관", "문화", "서촌", "경복궁역", "가볍게 둘러보고 산책하기 좋은 문화 코스입니다.", List.of("주말 낮 추천", "사진 찍기 좋음")),
            new CandidateVenue("한강 러닝 스테이션", "운동", "여의도", "여의나루역", "야외 운동 후 식사까지 이어가기 좋은 장소입니다.", List.of("야간 조명 좋음", "락커 이용 가능")),
            new CandidateVenue("성수 팝업 스트리트", "기타", "성수", "성수역", "팝업과 쇼핑 위주로 가볍게 만나기 좋습니다.", List.of("주말 볼거리 많음", "카페 연계 쉬움"))
    );

    private final Map<String, Map<String, Integer>> zoneTravelMinutes = Map.of(
            "중구", Map.of("을지로", 12, "합정", 28, "연남", 30, "성수", 21, "종로", 15, "서촌", 18, "여의도", 26),
            "성수", Map.of("을지로", 21, "합정", 33, "연남", 28, "성수", 10, "종로", 25, "서촌", 31, "여의도", 34),
            "공덕", Map.of("을지로", 20, "합정", 16, "연남", 18, "성수", 30, "종로", 18, "서촌", 17, "여의도", 14),
            "홍대", Map.of("을지로", 25, "합정", 10, "연남", 8, "성수", 29, "종로", 22, "서촌", 24, "여의도", 17),
            "여의도", Map.of("을지로", 24, "합정", 18, "연남", 21, "성수", 34, "종로", 24, "서촌", 25, "여의도", 8),
            "기본", Map.of("을지로", 24, "합정", 24, "연남", 24, "성수", 24, "종로", 24, "서촌", 24, "여의도", 24)
    );

    public ApiRecommendationService(InMemoryWemeetStore store) {
        this.store = store;
    }

    public ApiDtos.CategoryResponse categories() {
        return new ApiDtos.CategoryResponse(categories);
    }

    public ApiDtos.RecommendationResponse recommend(
            String requesterId,
            ApiDtos.RecommendationRequest request,
            ApiAuthService authService
    ) {
        String category = normalizeCategory(request.category());
        List<InMemoryWemeetStore.UserAccount> participants = resolveParticipants(requesterId, request.participantIds());

        List<ApiDtos.VenueResponse> venues = candidateVenues.stream()
                .filter(candidate -> candidate.category().equals(category))
                .map(candidate -> toVenueResponse(candidate, participants))
                .sorted(java.util.Comparator
                        .comparingInt(ApiDtos.VenueResponse::fairnessGap)
                        .thenComparingInt(ApiDtos.VenueResponse::averageMinutes)
                        .thenComparing(ApiDtos.VenueResponse::name))
                .limit(3)
                .toList();

        ApiDtos.VenueResponse bestVenue = venues.get(0);
        ApiDtos.MidpointResponse midpoint = new ApiDtos.MidpointResponse(
                bestVenue.area(),
                bestVenue.station(),
                bestVenue.averageMinutes(),
                bestVenue.fairnessGap(),
                "현재는 주소 기반 휴리스틱으로 계산하며, 이후 카카오 길찾기 API로 대체할 수 있습니다."
        );

        String query = midpoint.district() + " " + category;
        store.appendHistory(requesterId, query, category);

        return new ApiDtos.RecommendationResponse(
                category,
                participants.stream().map(authService::toUserResponse).toList(),
                midpoint,
                venues,
                "주소 키워드 기반 인메모리 계산"
        );
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

    private ApiDtos.VenueResponse toVenueResponse(
            CandidateVenue candidate,
            List<InMemoryWemeetStore.UserAccount> participants
    ) {
        List<ApiDtos.TravelTimeResponse> travelTimes = participants.stream()
                .map(participant -> new ApiDtos.TravelTimeResponse(
                        participant.id(),
                        participant.nickname(),
                        lookupTravelMinutes(resolveZone(participant.baseAddress()), candidate.area())
                ))
                .toList();

        int min = travelTimes.stream().mapToInt(ApiDtos.TravelTimeResponse::minutes).min().orElse(0);
        int max = travelTimes.stream().mapToInt(ApiDtos.TravelTimeResponse::minutes).max().orElse(0);
        int average = (int) Math.round(travelTimes.stream().mapToInt(ApiDtos.TravelTimeResponse::minutes).average().orElse(0));
        int fairnessGap = max - min;
        String reason = fairnessGap <= 8
                ? "선택한 인원 기준으로 이동시간 차이가 비교적 작습니다."
                : "모임은 가능하지만 일부 인원은 더 오래 이동해야 합니다.";

        return new ApiDtos.VenueResponse(
                candidate.name(),
                candidate.category(),
                candidate.area(),
                candidate.station(),
                candidate.description(),
                reason,
                fairnessGap,
                average,
                candidate.highlights(),
                travelTimes
        );
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
        return "기본";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank() || !categories.contains(category)) {
            return "맛집";
        }
        return category;
    }

    private record CandidateVenue(
            String name,
            String category,
            String area,
            String station,
            String description,
            List<String> highlights
    ) {
    }
}
