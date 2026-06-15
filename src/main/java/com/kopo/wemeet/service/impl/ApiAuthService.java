package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.*;

import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.repository.entity.PasswordResetToken;
import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.FriendRelationRepository;
import com.kopo.wemeet.repository.MeetingParticipantRepository;
import com.kopo.wemeet.repository.MeetingRepository;
import com.kopo.wemeet.repository.PasswordResetTokenRepository;
import com.kopo.wemeet.repository.SearchHistoryRepository;
import com.kopo.wemeet.session.RefreshTokenStore;
import com.kopo.wemeet.session.SessionTokenStore;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.impl.MailDeliveryService.MailSendResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class ApiAuthService implements IApiAuthService {
    // 회원가입, 로그인, 비밀번호 재설정처럼 인증과 계정 관리에 관한 핵심 로직을 모아 둔 서비스다.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AppUserRepository userRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenStore sessionTokenStore;
    private final RefreshTokenStore refreshTokenStore;
    private final MailDeliveryService mailDeliveryService;
    private final JwtTokenService jwtTokenService;

    @Override
    public AuthDTO.AuthResponse signUp(AuthDTO.SignUpRequest request) {
        // 회원가입은 입력값 검증 -> 중복 확인 -> 사용자 저장 -> 세션 발급 순서로 진행한다.
        validateSignupRequest(request);

        if (userRepository.existsByLoginId(request.loginId())) {
            throw new ResponseStatusException(CONFLICT, "loginId already exists");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(CONFLICT, "email already exists");
        }

        AppUser user = new AppUser(
                "user-" + UUID.randomUUID().toString().substring(0, 8),
                request.loginId(),
                request.nickname().isBlank() ? request.loginId() : request.nickname(),
                request.email(),
                passwordEncoder.encode(request.password()),
                generateFriendCode(request.loginId()),
                request.baseAddress() == null || request.baseAddress().isBlank() ? "서울특별시 중구 명동길 74" : request.baseAddress()
        );
        userRepository.save(user);

        return createAuthResponse(user);
    }

    @Override
    public AuthDTO.AuthResponse login(AuthDTO.LoginRequest request) {
        // 로그인은 아이디로 사용자를 찾은 뒤 비밀번호 해시를 비교한다.
        AppUser user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }

        return createAuthResponse(user);
    }

    @Override
    public AuthDTO.AuthResponse createSessionForUser(String userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user not found"));

        return createAuthResponse(user);
    }

    @Override
    public AuthDTO.AuthResponse refreshAccessToken(String refreshToken) {
        JwtTokenService.TokenDetails tokenDetails = jwtTokenService.resolveRefreshTokenDetails(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token"));
        if (!refreshTokenStore.isValid(tokenDetails.tokenId(), tokenDetails.userId())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token");
        }
        refreshTokenStore.revoke(tokenDetails.tokenId());
        String userId = tokenDetails.userId();
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "User not found"));
        return createAuthResponse(user);
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        jwtTokenService.resolveRefreshTokenDetails(refreshToken)
                .ifPresent(tokenDetails -> refreshTokenStore.revoke(tokenDetails.tokenId()));
    }

    @Override
    public void writeTokenCookies(HttpServletResponse response, AuthDTO.AuthResponse authResponse) {
        if (authResponse == null || authResponse.token() == null || authResponse.refreshToken() == null) {
            return;
        }
        jwtTokenService.writeTokenCookies(response, authResponse.token(), authResponse.refreshToken());
    }

    @Override
    public void clearTokenCookies(HttpServletResponse response) {
        jwtTokenService.clearTokenCookies(response);
    }

    @Override
    public AuthDTO.PasswordResetResponse createPasswordResetToken(AuthDTO.PasswordResetRequest request) {
        // 비밀번호 재설정은 토큰 저장 후 메일 또는 명시적 dev preview 경로로만 전달한다.
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email is required");
        }
        validateEmailFormat(request.email());

        AppUser user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "email not found"));

        String rawToken = UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(
                user,
                tokenHash,
                LocalDateTime.now().plusMinutes(30)
        );
        passwordResetTokenRepository.save(token);

        MailSendResult mailSendResult = mailDeliveryService.sendPasswordResetToken(user.getEmail(), rawToken);
        if (!mailSendResult.sent()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, mailSendResult.message());
        }

        return new AuthDTO.PasswordResetResponse(
                mailSendResult.message(),
                mailSendResult.previewCode()
        );
    }

    @Transactional
    @Override
    public AuthDTO.PasswordResetResponse resetPassword(AuthDTO.PasswordResetConfirmRequest request) {
        // 사용 가능하고 만료되지 않은 토큰만 조회해서 비밀번호를 바꾼다.
        if (request.token() == null || request.token().isBlank() || request.newPassword() == null || request.newPassword().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "token and newPassword are required");
        }

        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashAndUsedFalseAndExpiresAtAfter(hashToken(request.token()), LocalDateTime.now())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "valid reset token not found"));

        AppUser user = token.getUser();
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        token.markUsed();

        userRepository.save(user);
        passwordResetTokenRepository.save(token);

        return new AuthDTO.PasswordResetResponse("비밀번호가 변경되었습니다.", null);
    }

    @Override
    public String findLoginIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email is required");
        }
        validateEmailFormat(email);

        AppUser user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "email not found"));
        return user.getLoginId();
    }

    @Override
    public String findLoginIdByNameAndEmail(String name, String email) {
        if (name == null || name.isBlank() || email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "name and email are required");
        }
        validateEmailFormat(email);

        AppUser user = userRepository.findByNicknameAndEmail(name.trim(), email.trim())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "name and email do not match"));
        return user.getLoginId();
    }

    @Transactional
    @Override
    public AuthDTO.PasswordResetResponse issueTemporaryPassword(String loginId, String email) {
        if (loginId == null || loginId.isBlank() || email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "loginId and email are required");
        }
        validateEmailFormat(email);

        AppUser user = userRepository.findByLoginIdAndEmail(loginId.trim(), email.trim())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "loginId and email do not match"));

        String temporaryPassword = generateTemporaryPassword();
        user.changePasswordHash(passwordEncoder.encode(temporaryPassword));
        userRepository.save(user);
        MailSendResult mailSendResult = mailDeliveryService.sendTemporaryPassword(
                user.getEmail(),
                user.getLoginId(),
                temporaryPassword
        );
        if (!mailSendResult.sent()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, mailSendResult.message());
        }
        return new AuthDTO.PasswordResetResponse(mailSendResult.message(), mailSendResult.previewCode());
    }

    @Override
    public boolean isLoginIdAvailable(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "loginId is required");
        }
        return !userRepository.existsByLoginId(loginId.trim());
    }

    @Transactional
    @Override
    public void resetPasswordForUser(String userId, String newPassword) {
        if (userId == null || userId.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "userId and newPassword are required");
        }

        AppUser user = userRepository.findById(userId.trim())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user not found"));
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    @Override
    public void deleteUserAccount(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "userId is required");
        }

        AppUser user = userRepository.findById(userId.trim())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user not found"));

        meetingRepository.deleteAll(meetingRepository.findAllCreatedByUserId(user.getId()));
        meetingParticipantRepository.deleteByUserId(user.getId());
        friendRelationRepository.deleteByUserIdOrFriendId(user.getId(), user.getId());
        searchHistoryRepository.deleteByUserId(user.getId());
        passwordResetTokenRepository.deleteByUserId(user.getId());
        userRepository.delete(user);
    }

    @Override
    public boolean isEmailAvailable(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email is required");
        }
        validateEmailFormat(email);
        return !userRepository.existsByEmail(email.trim());
    }

    @Override
    public String createSignupEmailVerificationCode(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email is required");
        }
        validateEmailFormat(email);
        if (userRepository.existsByEmail(email.trim())) {
            throw new ResponseStatusException(CONFLICT, "email already exists");
        }
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    @Override
    public boolean matchesPassword(AppUser user, String rawPassword) {
        if (user == null || rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Transactional
    @Override
    public UserDTO.UserResponse updateBaseAddress(AppUser user, String baseAddress) {
        // 주소가 바뀌면 이후 추천 계산에 쓰일 출발지도 함께 바뀐다.
        if (baseAddress == null || baseAddress.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "baseAddress is required");
        }

        user.changeBaseAddress(baseAddress.trim());
        AppUser savedUser = userRepository.save(user);
        return toUserResponse(savedUser);
    }

    @Override
    public AppUser requireUser(String authorizationHeader) {
        // REST API에서는 세션 대신 Bearer 토큰으로 사용자를 식별한다.
        return requireUser(authorizationHeader, null);
    }

    @Override
    public AppUser requireUser(String authorizationHeader, String accessTokenCookie) {
        String bearerToken = extractBearerToken(authorizationHeader);
        String resolvedToken = bearerToken;
        if (resolvedToken == null && accessTokenCookie != null && !accessTokenCookie.isBlank()) {
            resolvedToken = accessTokenCookie.trim();
        }
        if (resolvedToken == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authorization header must use Bearer token");
        }
        String token = resolvedToken;
        String userId = jwtTokenService.resolveAccessTokenUserId(token)
                .or(() -> sessionTokenStore.findUserId(token))
                .orElse(null);
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid access token");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "User not found"));
    }

    @Override
    public UserDTO.UserResponse toUserResponse(AppUser user) {
        return new UserDTO.UserResponse(
                user.getId(),
                user.getNickname(),
                user.getLoginId(),
                user.getEmail(),
                user.getFriendCode(),
                user.getBaseAddress()
        );
    }

    @Override
    public UserDTO.UserResponse toUserResponse(UserDTO.UserAccount user) {
        AppUser persistentUser = userRepository.findById(user.id())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found: " + user.id()));
        return new UserDTO.UserResponse(
                persistentUser.getId(),
                persistentUser.getNickname(),
                persistentUser.getLoginId(),
                persistentUser.getEmail(),
                persistentUser.getFriendCode(),
                persistentUser.getBaseAddress()
        );
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }

        String trimmed = authorizationHeader.trim();
        String bearerPrefix = "bearer ";
        if (!trimmed.regionMatches(true, 0, bearerPrefix, 0, bearerPrefix.length())) {
            return null;
        }

        String token = trimmed.substring(bearerPrefix.length()).trim();
        return token.isBlank() ? null : token;
    }

    private void validateSignupRequest(AuthDTO.SignUpRequest request) {
        // 수업용 예제에서는 필수값 검증을 서비스에서 먼저 처리한다.
        if (request.loginId() == null || request.loginId().isBlank()
                || request.password() == null || request.password().isBlank()
                || request.email() == null || request.email().isBlank()
                || request.nickname() == null || request.nickname().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "nickname, loginId, password, and email are required");
        }
        validateEmailFormat(request.email());
    }

    private void validateEmailFormat(String email) {
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid email format");
        }
    }

    private String createSession(String userId) {
        String token = UUID.randomUUID().toString();
        // 세션 저장 구현은 Redis 또는 메모리 fallback 중 현재 설정에 맞는 쪽이 선택된다.
        sessionTokenStore.store(token, userId);
        return token;
    }

    private AuthDTO.AuthResponse createAuthResponse(AppUser user) {
        if (!jwtTokenService.isEnabled()) {
            return new AuthDTO.AuthResponse(createSession(user.getId()), toUserResponse(user));
        }
        return new AuthDTO.AuthResponse(
                jwtTokenService.createAccessToken(user.getId()),
                registerRefreshToken(jwtTokenService.createRefreshToken(user.getId())),
                toUserResponse(user)
        );
    }

    private String registerRefreshToken(String refreshToken) {
        jwtTokenService.resolveRefreshTokenDetails(refreshToken)
                .ifPresent(tokenDetails -> refreshTokenStore.store(
                        tokenDetails.tokenId(),
                        tokenDetails.userId(),
                        tokenDetails.expiresAt()
                ));
        return refreshToken;
    }

    private String generateFriendCode(String loginId) {
        // 친구코드는 사람이 입력하기 쉬운 형태를 위해 로그인 ID + 짧은 난수 조합으로 만든다.
        String base = loginId.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return (base.isBlank() ? "FRIEND" : base) + suffix;
    }

    private String generateTemporaryPassword() {
        return "WM" + String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000)) + "!";
    }

    private String hashToken(String rawToken) {
        try {
            // 재설정 토큰 원문을 그대로 저장하지 않고 해시값만 DB에 보관한다.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}

