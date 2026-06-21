package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.*;


/**
 * IApiRecommendationService는 구현체가 지켜야 할 서비스 계층 계약을 정의합니다.
 */
public interface IApiRecommendationService {
    // 카테고리 조회와 추천 계산 기능을 제공하는 서비스 계약이다.

    RecommendationDTO.CategoryResponse categories();

    RecommendationDTO.RecommendationResponse recommend(
            String requesterId,
            RecommendationDTO.RecommendationRequest request,
            IApiAuthService authService
    );

    RecommendationDTO.RecommendationResponse recommendForGuest(
            UserDTO.UserResponse guestUser,
            RecommendationDTO.RecommendationRequest request
    );
}
