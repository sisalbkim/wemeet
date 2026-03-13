package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.entity.AppUser;
import com.kopo.wemeet.repository.InMemoryWemeetStore;

public interface IApiAuthService {

    ApiDtos.AuthResponse signUp(ApiDtos.SignUpRequest request);

    ApiDtos.AuthResponse login(ApiDtos.LoginRequest request);

    ApiDtos.PasswordResetResponse createPasswordResetToken(ApiDtos.PasswordResetRequest request);

    ApiDtos.PasswordResetResponse resetPassword(ApiDtos.PasswordResetConfirmRequest request);

    ApiDtos.UserResponse updateBaseAddress(AppUser user, String baseAddress);

    AppUser requireUser(String authorizationHeader);

    ApiDtos.UserResponse toUserResponse(AppUser user);

    ApiDtos.UserResponse toUserResponse(InMemoryWemeetStore.UserAccount user);
}
