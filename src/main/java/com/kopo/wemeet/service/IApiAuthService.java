package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.entity.AppUser;
import com.kopo.wemeet.repository.WemeetDataStore;

public interface IApiAuthService {
    // 인증/회원 정보를 다루는 서비스 계약이다.

    ApiDtos.AuthResponse signUp(ApiDtos.SignUpRequest request);

    ApiDtos.AuthResponse login(ApiDtos.LoginRequest request);

    ApiDtos.PasswordResetResponse createPasswordResetToken(ApiDtos.PasswordResetRequest request);

    ApiDtos.PasswordResetResponse resetPassword(ApiDtos.PasswordResetConfirmRequest request);

    ApiDtos.UserResponse updateBaseAddress(AppUser user, String baseAddress);

    // 토큰에서 실제 사용자 엔티티를 찾아야 할 때 사용한다.
    AppUser requireUser(String authorizationHeader);

    // 엔티티나 임시 저장소 객체를 API 응답 DTO로 통일해 변환한다.
    ApiDtos.UserResponse toUserResponse(AppUser user);

    ApiDtos.UserResponse toUserResponse(WemeetDataStore.UserAccount user);
}
