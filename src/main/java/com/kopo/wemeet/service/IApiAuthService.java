package com.kopo.wemeet.service;

import com.kopo.wemeet.dto.*;

import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.repository.WemeetDataStore;

public interface IApiAuthService {
    // 인증/회원 정보를 다루는 서비스 계약이다.

    AuthDTO.AuthResponse signUp(AuthDTO.SignUpRequest request);

    AuthDTO.AuthResponse login(AuthDTO.LoginRequest request);

    AuthDTO.PasswordResetResponse createPasswordResetToken(AuthDTO.PasswordResetRequest request);

    AuthDTO.PasswordResetResponse resetPassword(AuthDTO.PasswordResetConfirmRequest request);

    String findLoginIdByNameAndEmail(String name, String email);

    String findLoginIdByEmail(String email);

    String issueTemporaryPassword(String name, String loginId, String email);

    String findUserIdByLoginIdAndEmail(String loginId, String email);

    boolean isEmailAvailable(String email);

    String createSignupEmailVerificationCode(String email);

    boolean matchesPassword(AppUser user, String rawPassword);

    void resetPasswordForUser(String userId, String newPassword);

    UserDTO.UserResponse updateBaseAddress(AppUser user, String baseAddress);

    // 토큰에서 실제 사용자 엔티티를 찾아야 할 때 사용한다.
    AppUser requireUser(String authorizationHeader);

    // 엔티티나 임시 저장소 객체를 API 응답 DTO로 통일해 변환한다.
    UserDTO.UserResponse toUserResponse(AppUser user);

    UserDTO.UserResponse toUserResponse(WemeetDataStore.UserAccount user);
}
