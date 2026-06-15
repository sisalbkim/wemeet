package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.*;

import com.kopo.wemeet.mapper.RecommendationBundleMapper;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.support.UserAccountLookup;
import com.kopo.wemeet.service.IFriendService;
import com.kopo.wemeet.service.IHistoryService;
import com.kopo.wemeet.service.IMeetingService;
import com.kopo.wemeet.service.IWemeetViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WemeetViewService implements IWemeetViewService {
    // 서비스 계층의 응답을 화면 전용 모델로 바꿔주는 어댑터 역할을 한다.
    // 컨트롤러가 템플릿 세부 구조를 너무 많이 알지 않게 하기 위해 분리했다.

    private final ApiRecommendationService recommendationService;
    private final ApiAuthService authService;
    private final UserAccountLookup userAccountLookup;
    private final IFriendService friendService;
    private final IHistoryService historyService;
    private final IMeetingService meetingService;
    private final RecommendationBundleMapper recommendationBundleMapper;
    private final DateTimeFormatter historyFormatter = DateTimeFormatter.ofPattern("yyyy. M. d.");

    private final List<RecommendationDTO.CategoryChip> categories = List.of(
            new RecommendationDTO.CategoryChip("전체", "전체"),
            new RecommendationDTO.CategoryChip("맛집", "맛집"),
            new RecommendationDTO.CategoryChip("카페", "카페"),
            new RecommendationDTO.CategoryChip("놀이", "놀이"),
            new RecommendationDTO.CategoryChip("문화", "문화"),
            new RecommendationDTO.CategoryChip("운동", "운동"),
            new RecommendationDTO.CategoryChip("기타", "기타")
    );

    @Override
    public UserDTO.UserProfile getGuestUser() {
        return getGuestUser("서울특별시 중구 명동길 74");
    }

    @Override
    public UserDTO.UserProfile getGuestUser(String baseAddress) {
        // 비로그인 체험 화면에서 사용할 기본 사용자 정보다.
        String normalizedBaseAddress = baseAddress == null || baseAddress.isBlank()
                ? "서울특별시 중구 명동길 74"
                : baseAddress.trim();
        return new UserDTO.UserProfile(
                "user-123",
                "김철수",
                "@FRIEND123",
                "FRIEND123",
                normalizedBaseAddress,
                "••••••••",
                2,
                2,
                3
        );
    }

    @Override
    public List<RecommendationDTO.CategoryChip> getCategories() {
        return categories;
    }

    @Override
    public List<RecommendationDTO.CategoryChip> getSelectableCategories() {
        return categories.stream()
                .filter(chip -> !"전체".equals(chip.label()))
                .toList();
    }

    @Override
    public List<FriendDTO.FriendSummary> getFriends(String userId) {
        return getFriends(userId, "");
    }

    @Override
    public List<FriendDTO.FriendSummary> getFriends(String userId, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        // 저장소 데이터를 화면에서 바로 쓰기 쉽게 FriendDTO.FriendSummary 형태로 변환한다.
        return friendService.listFriends(userId).stream()
                .map(friend -> new FriendDTO.FriendSummary(
                        friend.id(),
                        friend.nickname(),
                        "@" + friend.friendCode(),
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
    public List<FriendDTO.FriendRequest> getFriendRequests(String userId) {
        return friendService.listIncomingFriendRequests(userId).stream()
                .map(this::toFriendRequest)
                .toList();
    }

    @Override
    public List<FriendDTO.FriendRequest> getSentFriendRequests(String userId) {
        return friendService.listOutgoingFriendRequests(userId).stream()
                .map(this::toFriendRequest)
                .toList();
    }

    @Override
    public List<MeetingDTO.UpcomingMeeting> getUpcomingMeetings(String userId) {
        // 홈 화면에는 현재 사용자가 참여하는 오늘 이후 모임만 날짜순으로 보여준다.
        LocalDate today = LocalDate.now();
        return meetingService.listMeetingsForUser(userId).stream()
                .filter(meeting -> meeting.meetingDate() != null && !meeting.meetingDate().isBefore(today))
                .sorted(java.util.Comparator
                        .comparing(MeetingDTO.MeetingRecord::meetingDate)
                        .thenComparing(meeting -> meeting.meetingTime() == null ? java.time.LocalTime.MAX : meeting.meetingTime()))
                .limit(5)
                .map(this::toUpcomingMeeting)
                .toList();
    }

    @Override
    public List<MeetingDTO.CreatedMeeting> getCreatedMeetings(String userId) {
        return meetingService.listMeetingsCreatedByUser(userId).stream()
                .map(this::toCreatedMeeting)
                .toList();
    }

    @Override
    public List<MeetingDTO.CreatedMeeting> getParticipatingMeetings(String userId) {
        return meetingService.listMeetingsForUser(userId).stream()
                .filter(meeting -> !userId.equals(meeting.hostUserId()))
                .filter(meeting -> meeting.participantStatuses().stream()
                        .filter(participant -> participant.userId().equals(userId))
                        .noneMatch(participant -> "PENDING".equals(participant.status()) || "DECLINED".equals(participant.status())))
                .map(this::toCreatedMeeting)
                .toList();
    }

    @Override
    public List<MeetingDTO.MeetingInvitation> getMeetingInvitations(String userId) {
        return meetingService.listPendingInvitationsForUser(userId).stream()
                .map(this::toMeetingInvitation)
                .toList();
    }

    @Override
    public UserDTO.UserResponse addFriendByCode(String userId, String friendCode) {
        // 친구 코드를 입력하면 즉시 친구가 되지 않고 상대에게 승인 요청을 보낸다.
        return authService.toUserResponse(friendService.addFriendByCode(userId, friendCode));
    }

    @Override
    public void updateFriendFavorite(String userId, String friendId, boolean favorite) {
        friendService.updateFriendFavorite(userId, friendId, favorite);
    }

    @Override
    public void deleteFriend(String userId, String friendId) {
        friendService.deleteFriend(userId, friendId);
    }

    @Override
    public UserDTO.UserResponse respondFriendRequest(String userId, String requesterId, boolean approve) {
        return authService.toUserResponse(friendService.respondFriendRequest(userId, requesterId, approve));
    }

    @Override
    public List<HistoryDTO.SearchHistoryItem> getSearchHistory(String userId, String filter, String keyword) {
        String normalizedFilter = "전체".equals(filter) ? "전체" : normalizeCategory(filter);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        // 히스토리 화면은 사용자별 저장값을 필터링해서 바로 출력한다.
        return historyService.listHistory(userId).stream()
                .map(item -> new HistoryDTO.SearchHistoryItem(
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
        historyService.clearHistory(userId);
    }

    @Override
    public void removeSearchHistory(String userId, Long historyId) {
        historyService.removeHistory(userId, historyId);
    }

    @Override
    public RecommendationDTO.RecommendationBundle buildRecommendation(
            String requesterId,
            String category,
            String detailKeyword,
            List<String> selectedFriendIds,
            String mode,
            String anchorId,
            String routeMode
    ) {
        // 추천 서비스 응답을 Thymeleaf 템플릿에서 쓰는 화면 전용 모델로 다시 묶는다.
        RecommendationDTO.RecommendationResponse response = recommendationService.recommend(
                requesterId,
                new RecommendationDTO.RecommendationRequest(normalizeCategory(category), normalizeOptionalText(detailKeyword), selectedFriendIds, mode, anchorId, routeMode),
                authService
        );

        return recommendationBundleMapper.toBundle(response, "활성 사용자");
    }

    @Override
    public RecommendationDTO.RecommendationBundle buildGuestRecommendation(
            String baseAddress,
            String category,
            String detailKeyword,
            String mode,
            String anchorId,
            String routeMode
    ) {
        UserDTO.UserProfile guestProfile = getGuestUser(baseAddress);
        UserDTO.UserResponse guestUser = new UserDTO.UserResponse(
                guestProfile.id(),
                guestProfile.name(),
                "guest",
                "",
                guestProfile.friendCode(),
                guestProfile.baseAddress()
        );

        RecommendationDTO.RecommendationResponse response = recommendationService.recommendForGuest(
                guestUser,
                new RecommendationDTO.RecommendationRequest(normalizeCategory(category), normalizeOptionalText(detailKeyword), List.of(), mode, anchorId, routeMode)
        );

        return recommendationBundleMapper.toBundle(response, "게스트");
    }

    private String normalizeCategory(String category) {
        // 화면에서 "전체"를 선택한 경우 실제 추천 계산은 대표 카테고리인 맛집으로 보낸다.
        if (category == null || category.isBlank() || "전체".equals(category)) {
            return "맛집";
        }
        return category;
    }

    private String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
    }

    private FriendDTO.FriendRequest toFriendRequest(FriendDTO.FriendRequestEntry request) {
        return new FriendDTO.FriendRequest(
                request.id(),
                request.nickname(),
                "@" + request.friendCode(),
                request.baseAddress(),
                request.requestedOn().format(historyFormatter)
        );
    }

    private MeetingDTO.CreatedMeeting toCreatedMeeting(MeetingDTO.MeetingRecord meeting) {
        return new MeetingDTO.CreatedMeeting(
                meeting.id(),
                meeting.title(),
                meeting.description(),
                meeting.meetingDate().format(historyFormatter),
                meeting.meetingTime() == null ? "시간 미정" : meeting.meetingTime().toString(),
                meeting.category(),
                meeting.meetingPlaceName() == null || meeting.meetingPlaceName().isBlank() ? "만날 지점 미정" : meeting.meetingPlaceName(),
                meeting.meetingPlaceAddress() == null ? "" : meeting.meetingPlaceAddress(),
                (int) meeting.participantStatuses().stream()
                        .filter(participant -> "ACCEPTED".equals(participant.status()))
                        .count(),
                meeting.meetingPlaceName() == null || meeting.meetingPlaceName().isBlank()
                        ? ""
                        : "https://map.naver.com/p/search/"
                        + UriUtils.encodePathSegment(meeting.meetingPlaceName().trim(), StandardCharsets.UTF_8)
        );
    }

    private MeetingDTO.MeetingInvitation toMeetingInvitation(MeetingDTO.MeetingRecord meeting) {
        String hostName = userAccountLookup.findById(meeting.hostUserId())
                .map(UserDTO.UserAccount::nickname)
                .orElse("알 수 없음");
        return new MeetingDTO.MeetingInvitation(
                meeting.id(),
                meeting.title(),
                hostName,
                meeting.meetingDate().format(historyFormatter),
                meeting.meetingTime() == null ? "시간 미정" : meeting.meetingTime().toString(),
                meeting.meetingPlaceName() == null || meeting.meetingPlaceName().isBlank() ? "만날 지점 미정" : meeting.meetingPlaceName()
        );
    }

    private MeetingDTO.UpcomingMeeting toUpcomingMeeting(MeetingDTO.MeetingRecord meeting) {
        String hostName = userAccountLookup.findById(meeting.hostUserId())
                .map(UserDTO.UserAccount::nickname)
                .orElse("알 수 없음");
        boolean isToday = LocalDate.now().equals(meeting.meetingDate());
        return new MeetingDTO.UpcomingMeeting(
                meeting.title(),
                meeting.description() == null || meeting.description().isBlank() ? "등록된 설명이 없습니다." : meeting.description(),
                meeting.meetingDate().format(historyFormatter),
                hostName,
                isToday ? "진행중" : "예정",
                isToday ? "active" : "scheduled"
        );
    }
}

