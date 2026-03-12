package com.kopo.wemeet.ui;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class WemeetViewService {

    private final UiModels.UserProfile currentUser = new UiModels.UserProfile(
            "user-123",
            "김철수",
            "@user123",
            "FRIEND123",
            "서울특별시 중구 명동길 74",
            "••••••••",
            2,
            2,
            3
    );

    private final List<UiModels.CategoryChip> categories = List.of(
            new UiModels.CategoryChip("전체", "전체"),
            new UiModels.CategoryChip("맛집", "맛집"),
            new UiModels.CategoryChip("카페", "카페"),
            new UiModels.CategoryChip("놀이", "놀이"),
            new UiModels.CategoryChip("문화", "문화"),
            new UiModels.CategoryChip("운동", "운동"),
            new UiModels.CategoryChip("기타", "기타")
    );

    private final List<UiModels.FriendSummary> friends = List.of(
            new UiModels.FriendSummary("friend-lee", "이영희", "@user456", "성수동 출발", "2026. 3. 1."),
            new UiModels.FriendSummary("friend-park", "박민수", "@user789", "공덕역 출발", "2026. 3. 5.")
    );

    private final List<UiModels.FriendRequest> friendRequests = List.of(
            new UiModels.FriendRequest("최지우", "@user321")
    );

    private final List<UiModels.UpcomingMeeting> upcomingMeetings = List.of(
            new UiModels.UpcomingMeeting("주말 맛집 탐방", "주말에 새로운 맛집 찾아가요!", "2026. 3. 15.", "김철수", "예정", "scheduled"),
            new UiModels.UpcomingMeeting("친구들과 보드게임", "보드게임 카페에서 즐겁게 놀아요", "2026. 3. 20.", "이영희", "예정", "scheduled"),
            new UiModels.UpcomingMeeting("카페 스터디", "조용한 카페에서 같이 공부해요", "2026. 3. 18.", "김철수", "진행중", "active")
    );

    private final List<UiModels.SearchHistoryItem> searchHistory = List.of(
            new UiModels.SearchHistoryItem("강남 맛집", "맛집", "2026. 3. 10."),
            new UiModels.SearchHistoryItem("홍대 카페", "카페", "2026. 3. 8."),
            new UiModels.SearchHistoryItem("신촌 보드게임", "놀이", "2026. 3. 5.")
    );

    private final List<CandidateVenue> candidateVenues = List.of(
            new CandidateVenue(
                    "을지다락 서울숲점",
                    "맛집",
                    "을지로",
                    "을지로3가역",
                    "세 명이 비슷한 시간대에 만나기 쉬운 파스타 중심 식당입니다.",
                    List.of("대기 줄 짧은 편", "식사 후 카페 이동 쉬움"),
                    Map.of("user-123", 18, "friend-lee", 23, "friend-park", 26)
            ),
            new CandidateVenue(
                    "합정 푸드라운지",
                    "맛집",
                    "합정",
                    "합정역",
                    "식사 후 바로 놀거리로 이어가기 쉬운 복합 상권입니다.",
                    List.of("지하철 접근성 좋음", "저녁 모임 적합"),
                    Map.of("user-123", 31, "friend-lee", 29, "friend-park", 24)
            ),
            new CandidateVenue(
                    "연남 북카페",
                    "카페",
                    "연남",
                    "홍대입구역",
                    "대화와 작업 모두 가능한 넓은 좌석 중심 카페입니다.",
                    List.of("오래 머무르기 좋음", "디저트 선택 다양"),
                    Map.of("user-123", 28, "friend-lee", 24, "friend-park", 30)
            ),
            new CandidateVenue(
                    "서울숲 로스터리",
                    "카페",
                    "성수",
                    "서울숲역",
                    "분위기 중심의 카페로 첫 모임 장소로 무난합니다.",
                    List.of("낮 시간 추천", "산책 코스 연계"),
                    Map.of("user-123", 22, "friend-lee", 12, "friend-park", 29)
            ),
            new CandidateVenue(
                    "합정 보드게임 라운지",
                    "놀이",
                    "합정",
                    "합정역",
                    "보드게임과 식음료를 한 번에 해결할 수 있는 장소입니다.",
                    List.of("초보자용 게임 많음", "주말 예약 추천"),
                    Map.of("user-123", 29, "friend-lee", 27, "friend-park", 25)
            ),
            new CandidateVenue(
                    "종로 방탈출 아지트",
                    "놀이",
                    "종로",
                    "종각역",
                    "활동적인 일정으로 이어가기 좋은 실내 놀거리입니다.",
                    List.of("저녁 타임 다양", "인근 맛집 많음"),
                    Map.of("user-123", 16, "friend-lee", 26, "friend-park", 23)
            ),
            new CandidateVenue(
                    "서촌 전시관",
                    "문화",
                    "서촌",
                    "경복궁역",
                    "가볍게 둘러보기 좋은 전시와 산책 동선을 함께 제공합니다.",
                    List.of("주말 낮 추천", "사진 찍기 좋음"),
                    Map.of("user-123", 22, "friend-lee", 33, "friend-park", 21)
            ),
            new CandidateVenue(
                    "한강 러닝 스테이션",
                    "운동",
                    "여의도",
                    "여의나루역",
                    "가볍게 뛰고 식사까지 이어가기 좋은 야외 운동 장소입니다.",
                    List.of("락커 이용 가능", "야간 조명 우수"),
                    Map.of("user-123", 27, "friend-lee", 32, "friend-park", 19)
            ),
            new CandidateVenue(
                    "성수 팝업 스트리트",
                    "기타",
                    "성수",
                    "성수역",
                    "팝업 스토어와 쇼핑 위주로 가볍게 만나기 좋습니다.",
                    List.of("주말 볼거리 많음", "카페 연계 쉬움"),
                    Map.of("user-123", 25, "friend-lee", 15, "friend-park", 31)
            )
    );

    public UiModels.UserProfile getCurrentUser() {
        return currentUser;
    }

    public List<UiModels.CategoryChip> getCategories() {
        return categories;
    }

    public List<UiModels.FriendSummary> getFriends() {
        return friends;
    }

    public List<UiModels.FriendRequest> getFriendRequests() {
        return friendRequests;
    }

    public List<UiModels.UpcomingMeeting> getUpcomingMeetings() {
        return upcomingMeetings;
    }

    public List<UiModels.SearchHistoryItem> getSearchHistory(String filter, String keyword) {
        String normalizedFilter = "전체".equals(filter) ? "전체" : normalizeCategory(filter);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        return searchHistory.stream()
                .filter(item -> "전체".equals(normalizedFilter) || item.category().equals(normalizedFilter))
                .filter(item -> normalizedKeyword.isBlank() || item.query().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .toList();
    }

    public UiModels.RecommendationBundle buildRecommendation(String category, List<String> selectedFriendIds) {
        String normalizedCategory = normalizeCategory(category);
        List<UiModels.FriendSummary> participants = new ArrayList<>();
        participants.add(asParticipant(currentUser));

        List<String> requestedIds = selectedFriendIds == null ? List.of() : selectedFriendIds;
        List<UiModels.FriendSummary> selectedFriends = friends.stream()
                .filter(friend -> requestedIds.isEmpty() || requestedIds.contains(friend.id()))
                .toList();

        if (selectedFriends.isEmpty()) {
            selectedFriends = List.of(friends.get(0));
        }

        participants.addAll(selectedFriends);

        List<UiModels.VenueOption> venues = candidateVenues.stream()
                .filter(candidate -> candidate.category().equals(normalizedCategory))
                .map(candidate -> toVenueOption(candidate, participants))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(UiModels.VenueOption::fairnessGap)
                        .thenComparingInt(UiModels.VenueOption::averageMinutes)
                        .thenComparing(UiModels.VenueOption::name))
                .limit(3)
                .toList();

        UiModels.VenueOption bestVenue = venues.isEmpty() ? fallbackVenue(normalizedCategory, participants) : venues.get(0);
        UiModels.MidpointSummary midpoint = new UiModels.MidpointSummary(
                bestVenue.area(),
                deriveStation(bestVenue.area()),
                bestVenue.averageMinutes(),
                bestVenue.fairnessGap(),
                "선택 인원 기준으로 대중교통 이동시간 편차가 가장 작은 후보를 먼저 보여줍니다."
        );

        return new UiModels.RecommendationBundle(
                normalizedCategory,
                participants,
                midpoint,
                venues.isEmpty() ? List.of(bestVenue) : venues,
                "카카오 길찾기 API 연결 전 더미 데이터 기반"
        );
    }

    private UiModels.FriendSummary asParticipant(UiModels.UserProfile profile) {
        return new UiModels.FriendSummary(profile.id(), profile.name(), profile.handle(), "명동 출발", "기본 사용자");
    }

    private UiModels.VenueOption toVenueOption(CandidateVenue candidate, List<UiModels.FriendSummary> participants) {
        List<UiModels.TravelTime> travelTimes = participants.stream()
                .map(participant -> new UiModels.TravelTime(
                        participant.name(),
                        candidate.travelMinutes().getOrDefault(participant.id(), 30)
                ))
                .toList();

        int min = travelTimes.stream().mapToInt(UiModels.TravelTime::minutes).min().orElse(0);
        int max = travelTimes.stream().mapToInt(UiModels.TravelTime::minutes).max().orElse(0);
        int average = (int) Math.round(travelTimes.stream().mapToInt(UiModels.TravelTime::minutes).average().orElse(0));
        int fairnessGap = max - min;

        String reason = fairnessGap <= 6
                ? "이동시간 차이가 작아서 가장 공평한 선택입니다."
                : "평균 이동시간은 적당하지만 일부 인원은 조금 더 이동해야 합니다.";

        return new UiModels.VenueOption(
                candidate.name(),
                candidate.category(),
                candidate.area(),
                candidate.description(),
                reason,
                fairnessGap,
                average,
                candidate.highlights(),
                travelTimes
        );
    }

    private UiModels.VenueOption fallbackVenue(String category, List<UiModels.FriendSummary> participants) {
        List<UiModels.TravelTime> travelTimes = participants.stream()
                .map(friend -> new UiModels.TravelTime(friend.name(), 25))
                .toList();

        return new UiModels.VenueOption(
                "추천 후보 준비중",
                category,
                "중앙권",
                "실제 지도 API 연결 후 이 영역에 카카오 기반 추천 결과가 표시됩니다.",
                "현재는 화면 연결을 위한 임시 결과입니다.",
                0,
                25,
                List.of("지도 API 연동 필요", "실시간 대중교통 계산 예정"),
                travelTimes
        );
    }

    private String deriveStation(String area) {
        Map<String, String> areaToStation = new LinkedHashMap<>();
        areaToStation.put("을지로", "을지로3가역");
        areaToStation.put("합정", "합정역");
        areaToStation.put("연남", "홍대입구역");
        areaToStation.put("성수", "성수역");
        areaToStation.put("중앙권", "시청역");
        return areaToStation.getOrDefault(area, area + "역");
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank() || "전체".equals(category)) {
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
            List<String> highlights,
            Map<String, Integer> travelMinutes
    ) {
    }
}
