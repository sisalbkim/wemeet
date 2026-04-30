package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.*;


import java.util.List;

public interface IWemeetViewService {
    // 템플릿 화면에서 필요한 데이터를 화면 전용 모델로 제공하는 서비스 계약이다.

    UserDTO.UserProfile getGuestUser();

    UserDTO.UserProfile getGuestUser(String baseAddress);

    List<RecommendationDTO.CategoryChip> getCategories();

    List<RecommendationDTO.CategoryChip> getSelectableCategories();

    List<FriendDTO.FriendSummary> getFriends(String userId);

    List<FriendDTO.FriendSummary> getFriends(String userId, String keyword);

    List<FriendDTO.FriendRequest> getFriendRequests(String userId);

    List<FriendDTO.FriendRequest> getSentFriendRequests(String userId);

    List<MeetingDTO.UpcomingMeeting> getUpcomingMeetings(String userId);

    List<MeetingDTO.CreatedMeeting> getCreatedMeetings(String userId);

    List<MeetingDTO.CreatedMeeting> getParticipatingMeetings(String userId);

    UserDTO.UserResponse addFriendByCode(String userId, String friendCode);

    UserDTO.UserResponse respondFriendRequest(String userId, String requesterId, boolean approve);

    void updateFriendFavorite(String userId, String friendId, boolean favorite);

    List<HistoryDTO.SearchHistoryItem> getSearchHistory(String userId, String filter, String keyword);

    void clearSearchHistory(String userId);

    void removeSearchHistory(String userId, Long historyId);

    RecommendationDTO.RecommendationBundle buildRecommendation(
            String requesterId,
            String category,
            List<String> selectedFriendIds,
            String mode,
            String anchorId,
            String routeMode
    );

    RecommendationDTO.RecommendationBundle buildGuestRecommendation(
            String baseAddress,
            String category,
            String mode,
            String anchorId,
            String routeMode
    );
}
