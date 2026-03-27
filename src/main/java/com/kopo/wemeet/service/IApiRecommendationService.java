package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.ApiDtos;

public interface IApiRecommendationService {
    // 카테고리 조회와 추천 계산 기능을 제공하는 서비스 계약이다.

    ApiDtos.CategoryResponse categories();

    ApiDtos.RecommendationResponse recommend(
            String requesterId,
            ApiDtos.RecommendationRequest request,
            IApiAuthService authService
    );

    ApiDtos.RecommendationResponse recommendForGuest(
            ApiDtos.UserResponse guestUser,
            ApiDtos.RecommendationRequest request
    );
}
