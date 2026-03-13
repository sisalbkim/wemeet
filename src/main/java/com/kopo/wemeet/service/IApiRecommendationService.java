package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.ApiDtos;

public interface IApiRecommendationService {

    ApiDtos.CategoryResponse categories();

    ApiDtos.RecommendationResponse recommend(
            String requesterId,
            ApiDtos.RecommendationRequest request,
            IApiAuthService authService
    );
}
