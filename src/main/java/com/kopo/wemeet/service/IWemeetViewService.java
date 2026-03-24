package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.dto.UiModels;

import java.util.List;

public interface IWemeetViewService {
    // 템플릿 화면에서 필요한 데이터를 화면 전용 모델로 제공하는 서비스 계약이다.

    UiModels.UserProfile getGuestUser();

    UiModels.UserProfile getGuestUser(String baseAddress);

    List<UiModels.CategoryChip> getCategories();

    List<UiModels.FriendSummary> getFriends(String userId);

    List<UiModels.FriendRequest> getFriendRequests();

    List<UiModels.UpcomingMeeting> getUpcomingMeetings();

    ApiDtos.UserResponse addFriendByCode(String userId, String friendCode);

    List<UiModels.SearchHistoryItem> getSearchHistory(String userId, String filter, String keyword);

    UiModels.RecommendationBundle buildRecommendation(
            String requesterId,
            String category,
            List<String> selectedFriendIds,
            String mode,
            String anchorId
    );

    UiModels.RecommendationBundle buildGuestRecommendation(
            String baseAddress,
            String category,
            String mode,
            String anchorId
    );
}
