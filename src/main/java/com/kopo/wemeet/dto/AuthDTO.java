package com.kopo.wemeet.dto;

/**
 * AuthDTO는 계층 간 데이터 전달과 화면/API 응답 구성을 위한 DTO 묶음입니다.
 */
public final class AuthDTO {
    // 인증 관련 요청과 응답 record를 한곳에 모아둔 DTO 모음이다.

    private AuthDTO() {
    }

    public record SignUpRequest(
            String nickname,
            String loginId,
            String password,
            String email,
            String baseAddress
    ) {
    }

    public record LoginRequest(
            String loginId,
            String password
    ) {
    }

    public record AuthResponse(
            String token,
            String refreshToken,
            UserDTO.UserResponse user
    ) {
        public AuthResponse(String token, UserDTO.UserResponse user) {
            this(token, null, user);
        }
    }

    public record RefreshRequest(
            String refreshToken
    ) {
    }

    public record PasswordResetRequest(
            String email
    ) {
    }

    public record PasswordResetConfirmRequest(
            String token,
            String newPassword
    ) {
    }

    public record PasswordResetResponse(
            String message,
            String resetTokenPreview
    ) {
    }

    public record EmailAvailabilityResponse(
            boolean available,
            String message
    ) {
    }

    public record LoginIdAvailabilityResponse(
            boolean available,
            String message
    ) {
    }

    public record EmailVerificationSendRequest(
            String email
    ) {
    }

    public record EmailVerificationSendResponse(
            boolean sent,
            String message,
            String codePreview
    ) {
    }

    public record EmailVerificationConfirmRequest(
            String email,
            String code
    ) {
    }

    public record EmailVerificationConfirmResponse(
            boolean verified,
            String message
    ) {
    }
}
