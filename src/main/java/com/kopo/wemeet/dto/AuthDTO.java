package com.kopo.wemeet.dto;

public final class AuthDTO {

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
            UserDTO.UserResponse user
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
