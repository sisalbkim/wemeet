package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.dto.UiModels;
import com.kopo.wemeet.entity.AppUser;
import com.kopo.wemeet.repository.WemeetDataStore;
import com.kopo.wemeet.service.IWemeetViewService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class WemeetViewService implements IWemeetViewService {
    // 서비스 계층의 응답을 화면 전용 모델로 바꿔주는 어댑터 역할을 한다.
    // 컨트롤러가 템플릿 세부 구조를 너무 많이 알지 않게 하기 위해 분리했다.

    private final ApiRecommendationService recommendationService;
    private final ApiAuthService authService;
    private final WemeetDataStore store;
    private final DateTimeFormatter historyFormatter = DateTimeFormatter.ofPattern("yyyy. M. d.");

    private final List<UiModels.CategoryChip> categories = List.of(
            new UiModels.CategoryChip("전체", "전체"),
            new UiModels.CategoryChip("맛집", "맛집"),
            new UiModels.CategoryChip("카페", "카페"),
            new UiModels.CategoryChip("놀이", "놀이"),
            new UiModels.CategoryChip("문화", "문화"),
            new UiModels.CategoryChip("운동", "운동"),
            new UiModels.CategoryChip("기타", "기타")
    );

    private final List<UiModels.UpcomingMeeting> upcomingMeetings = List.of(
            new UiModels.UpcomingMeeting("주말 맛집 탐방", "주말에 새로운 맛집 찾아가요!", "2026. 3. 15.", "김철수", "예정", "scheduled"),
            new UiModels.UpcomingMeeting("친구들과 보드게임", "보드게임 카페에서 즐겁게 놀아요", "2026. 3. 20.", "이영희", "예정", "scheduled"),
            new UiModels.UpcomingMeeting("카페 스터디", "조용한 카페에서 같이 공부해요", "2026. 3. 18.", "김철수", "진행중", "active")
    );

    public WemeetViewService(
            ApiRecommendationService recommendationService,
            ApiAuthService authService,
            WemeetDataStore store
    ) {
        this.recommendationService = recommendationService;
        this.authService = authService;
        this.store = store;
    }

    @Override
    public UiModels.UserProfile getGuestUser() {
        return getGuestUser("서울특별시 중구 명동길 74");
    }

    @Override
    public UiModels.UserProfile getGuestUser(String baseAddress) {
        // 비로그인 체험 화면에서 사용할 기본 사용자 정보다.
        String normalizedBaseAddress = baseAddress == null || baseAddress.isBlank()
                ? "서울특별시 중구 명동길 74"
                : baseAddress.trim();
        return new UiModels.UserProfile(
                "user-123",
                "김철수",
                "@user123",
                "FRIEND123",
                normalizedBaseAddress,
                "••••••••",
                2,
                2,
                3
        );
    }

    @Override
    public List<UiModels.CategoryChip> getCategories() {
        return categories;
    }

    @Override
    public List<UiModels.FriendSummary> getFriends(String userId) {
        return getFriends(userId, "");
    }

    @Override
    public List<UiModels.FriendSummary> getFriends(String userId, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        // 저장소 데이터를 화면에서 바로 쓰기 쉽게 FriendSummary 형태로 변환한다.
        return store.listFriends(userId).stream()
                .map(friend -> new UiModels.FriendSummary(
                        friend.id(),
                        friend.nickname(),
                        "@" + friend.loginId(),
                        friend.baseAddress(),
                        friend.joinedOn().format(historyFormatter),
                        friend.favorite()
                ))
                .filter(friend -> normalizedKeyword.isBlank()
                        || friend.name().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || friend.addressHint().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .toList();
    }

    @Override
    public List<UiModels.FriendRequest> getFriendRequests() {
        // 실제 친구 요청 기능이 붙기 전까지는 더미 요청을 노출하지 않는다.
        return List.of();
    }

    @Override
    public List<UiModels.UpcomingMeeting> getUpcomingMeetings() {
        // 모임 목록도 현재는 메인 화면 시연용 고정 데이터를 사용한다.
        return upcomingMeetings;
    }

    @Override
    public ApiDtos.UserResponse addFriendByCode(String userId, String friendCode) {
        // 화면 계층에서는 저장소를 직접 다루지 않고 인증 서비스의 DTO 변환 결과를 재사용한다.
        return authService.toUserResponse(store.addFriendByCode(userId, friendCode));
    }

    @Override
    public void updateFriendFavorite(String userId, String friendId, boolean favorite) {
        store.updateFriendFavorite(userId, friendId, favorite);
    }

    @Override
    public List<UiModels.SearchHistoryItem> getSearchHistory(String userId, String filter, String keyword) {
        String normalizedFilter = "전체".equals(filter) ? "전체" : normalizeCategory(filter);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        // 히스토리 화면은 사용자별 저장값을 필터링해서 바로 출력한다.
        return store.listHistory(userId).stream()
                .map(item -> new UiModels.SearchHistoryItem(
                        item.id(),
                        item.query(),
                        item.category(),
                        item.searchedAt().format(historyFormatter)
                ))
                .filter(item -> "전체".equals(normalizedFilter) || item.category().equals(normalizedFilter))
                .filter(item -> normalizedKeyword.isBlank() || item.query().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .toList();
    }

    @Override
    public void clearSearchHistory(String userId) {
        store.clearHistory(userId);
    }

    @Override
    public void removeSearchHistory(String userId, Long historyId) {
        store.removeHistory(userId, historyId);
    }

    @Override
    public UiModels.RecommendationBundle buildRecommendation(
            String requesterId,
            String category,
            List<String> selectedFriendIds,
            String mode,
            String anchorId
    ) {
        // 추천 서비스 응답을 Thymeleaf 템플릿에서 쓰는 화면 전용 모델로 다시 묶는다.
        ApiDtos.RecommendationResponse response = recommendationService.recommend(
                requesterId,
                new ApiDtos.RecommendationRequest(normalizeCategory(category), selectedFriendIds, mode, anchorId),
                authService
        );

        List<UiModels.FriendSummary> participants = response.participants().stream()
                .map(participant -> new UiModels.FriendSummary(
                        participant.id(),
                        participant.nickname(),
                        "@" + participant.loginId(),
                        participant.baseAddress(),
                        "활성 사용자",
                        false
                ))
                .toList();

        List<UiModels.VenueOption> venues = response.venues().stream()
                .map(venue -> new UiModels.VenueOption(
                        venue.name(),
                        venue.category(),
                        venue.area(),
                        venue.latitude(),
                        venue.longitude(),
                        venue.description(),
                        venue.telephone(),
                        venue.link(),
                        venue.reason(),
                        venue.fairnessGap(),
                        venue.averageMinutes(),
                        venue.highlights(),
                        venue.travelTimes().stream()
                                .map(time -> new UiModels.TravelTime(time.participantName(), time.minutes()))
                                .toList()
                ))
                .toList();

        return new UiModels.RecommendationBundle(
                response.category(),
                participants,
                new UiModels.MidpointSummary(
                        response.midpoint().district(),
                        response.midpoint().station(),
                        response.midpoint().latitude(),
                        response.midpoint().longitude(),
                        response.midpoint().averageMinutes(),
                        response.midpoint().fairnessGap(),
                        response.midpoint().note()
                ),
                venues,
                response.mapPoints().stream()
                        .map(point -> new UiModels.MapPoint(
                                point.id(),
                                point.label(),
                                point.address(),
                                point.latitude(),
                                point.longitude(),
                                point.markerType(),
                                point.selected()
                        ))
                        .toList(),
                response.calculationMode()
        );
    }

    @Override
    public UiModels.RecommendationBundle buildGuestRecommendation(
            String baseAddress,
            String category,
            String mode,
            String anchorId
    ) {
        UiModels.UserProfile guestProfile = getGuestUser(baseAddress);
        ApiDtos.UserResponse guestUser = new ApiDtos.UserResponse(
                guestProfile.id(),
                guestProfile.name(),
                guestProfile.handle().replaceFirst("^@", ""),
                "",
                guestProfile.friendCode(),
                guestProfile.baseAddress()
        );

        ApiDtos.RecommendationResponse response = recommendationService.recommendForGuest(
                guestUser,
                new ApiDtos.RecommendationRequest(normalizeCategory(category), List.of(), mode, anchorId)
        );

        return new UiModels.RecommendationBundle(
                response.category(),
                response.participants().stream()
                        .map(participant -> new UiModels.FriendSummary(
                                participant.id(),
                                participant.nickname(),
                                "@" + participant.loginId(),
                                participant.baseAddress(),
                                "게스트",
                                false
                        ))
                        .toList(),
                new UiModels.MidpointSummary(
                        response.midpoint().district(),
                        response.midpoint().station(),
                        response.midpoint().latitude(),
                        response.midpoint().longitude(),
                        response.midpoint().averageMinutes(),
                        response.midpoint().fairnessGap(),
                        response.midpoint().note()
                ),
                response.venues().stream()
                        .map(venue -> new UiModels.VenueOption(
                                venue.name(),
                                venue.category(),
                                venue.area(),
                                venue.latitude(),
                                venue.longitude(),
                                venue.description(),
                                venue.telephone(),
                                venue.link(),
                                venue.reason(),
                                venue.fairnessGap(),
                                venue.averageMinutes(),
                                venue.highlights(),
                                venue.travelTimes().stream()
                                        .map(time -> new UiModels.TravelTime(time.participantName(), time.minutes()))
                                        .toList()
                        ))
                        .toList(),
                response.mapPoints().stream()
                        .map(point -> new UiModels.MapPoint(
                                point.id(),
                                point.label(),
                                point.address(),
                                point.latitude(),
                                point.longitude(),
                                point.markerType(),
                                point.selected()
                        ))
                        .toList(),
                response.calculationMode()
        );
    }

    private String normalizeCategory(String category) {
        // 화면에서 "전체"를 선택한 경우 실제 추천 계산은 대표 카테고리인 맛집으로 보낸다.
        if (category == null || category.isBlank() || "전체".equals(category)) {
            return "맛집";
        }
        return category;
    }
}
