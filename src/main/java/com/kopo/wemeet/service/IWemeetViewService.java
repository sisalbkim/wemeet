package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.dto.UiModels;

import java.util.List;

public interface IWemeetViewService {

    UiModels.UserProfile getGuestUser();

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
}
