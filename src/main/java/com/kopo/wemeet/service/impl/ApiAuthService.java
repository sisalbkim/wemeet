package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.*;

import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.FriendRelationRepository;
import com.kopo.wemeet.repository.MeetingParticipantRepository;
import com.kopo.wemeet.repository.MeetingRepository;
import com.kopo.wemeet.repository.PasswordResetTokenRepository;
import com.kopo.wemeet.repository.SearchHistoryRepository;
import com.kopo.wemeet.session.RefreshTokenStore;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.impl.MailDeliveryService.MailSendResult;
import com.kopo.wemeet.util.CmmUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * ApiAuthService는 도메인 규칙과 외부 연동 흐름을 조합해 실제 비즈니스 처리를 수행합니다.
 */
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
    private final RefreshTokenStore refreshTokenStore;
    private final MailDeliveryService mailDeliveryService;
    private final JwtTokenService jwtTokenService;

    @Override
    public void signUp(AuthDTO.SignUpRequest request) {
        // 회원가입은 입력값 검증 -> 중복 확인 -> 사용자 저장 순서로 진행한다.
        validateSignupRequest(request); //문제 있으면 여기서 예외 발생

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
                CmmUtil.nvl(request.baseAddress()).trim()
        );
        userRepository.save(user);
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
    public void revokeRefreshToken(String refreshToken) {
        // 로그아웃 시 저장된 Refresh Token을 더 이상 쓸 수 없게 폐기한다.
        jwtTokenService.resolveRefreshTokenDetails(refreshToken)
                .ifPresent(tokenDetails -> refreshTokenStore.revoke(tokenDetails.tokenId()));
    }

    @Override
    public void writeTokenCookies(HttpServletResponse response, AuthDTO.AuthResponse authResponse) {
        // 기본 호출은 브라우저를 닫아도 유지되는 로그인 쿠키를 발급한다.
        writeTokenCookies(response, authResponse, true);
    }

    @Override
    public void writeTokenCookies(HttpServletResponse response, AuthDTO.AuthResponse authResponse, boolean persistent) {
        if (authResponse == null || authResponse.token() == null || authResponse.refreshToken() == null) {
            return;
        }
        // 화면 로그인은 autoLogin 값에 따라 persistent 여부를 넘기고, 실제 쿠키 생성은 JwtTokenService가 담당한다.
        jwtTokenService.writeTokenCookies(response, authResponse.token(), authResponse.refreshToken(), persistent);
    }

    @Override
    public void clearTokenCookies(HttpServletResponse response) {
        // 로그아웃 시 브라우저에 저장된 Access/Refresh/CSRF 쿠키를 삭제한다.
        jwtTokenService.clearTokenCookies(response);
    }

    @Override
    public AppUser refreshAccessToken(String refreshToken, HttpServletResponse response) {
        JwtTokenService.TokenDetails tokenDetails = jwtTokenService.resolveRefreshTokenDetails(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token"));
        if (!refreshTokenStore.isValid(tokenDetails.tokenId(), tokenDetails.userId())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Refresh token is not active");
        }

        AppUser user = userRepository.findById(tokenDetails.userId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "User not found"));
        jwtTokenService.writeRefreshedAccessCookie(response, jwtTokenService.createAccessToken(user.getId()));
        return user;
    }

    @Override
    public String findLoginIdByEmail(String email) {
        // 이메일만으로 가입된 아이디를 찾을 때 사용한다.
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
        // 이름과 이메일이 모두 일치하는 계정의 아이디를 찾는다.
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
        // 아이디와 이메일이 맞으면 임시 비밀번호로 교체하고 메일로 안내한다.
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
        // 회원가입 화면의 아이디 중복확인에서 사용한다.
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "loginId is required");
        }
        return !userRepository.existsByLoginId(loginId.trim());
    }

    @Transactional
    @Override
    public void resetPasswordForUser(String userId, String newPassword) {
        // 로그인한 사용자가 마이페이지에서 비밀번호를 직접 변경할 때 사용한다.
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
        // 회원탈퇴 시 사용자와 연결된 모임/친구/검색/토큰 데이터를 함께 정리한다.
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
        // 회원가입 화면의 이메일 중복확인에서 사용한다.
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email is required");
        }
        validateEmailFormat(email);
        return !userRepository.existsByEmail(email.trim());
    }

    @Override
    public String createSignupEmailVerificationCode(String email) {
        // 가입 가능한 이메일인지 확인한 뒤 6자리 인증코드를 만든다.
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
        // 비밀번호 확인 화면에서 입력값이 현재 비밀번호와 맞는지 검증한다.
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
        // Authorization 헤더나 Access Token 쿠키에서 현재 로그인 사용자를 찾는다.
        String bearerToken = extractBearerToken(authorizationHeader);
        String resolvedToken = bearerToken;
        if (resolvedToken == null && accessTokenCookie != null && !accessTokenCookie.isBlank()) {
            resolvedToken = accessTokenCookie.trim();
        }
        if (resolvedToken == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authorization header must use Bearer token");
        }
        String token = resolvedToken;
        String userId = jwtTokenService.resolveAccessTokenUserId(token).orElse(null);
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid access token");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "User not found"));
    }

    @Override
    public UserDTO.UserResponse toUserResponse(AppUser user) {
        // 엔티티에서 비밀번호 해시를 제외하고 화면/API에 안전한 사용자 정보만 만든다.
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
        // 임시 사용자 DTO의 id로 최신 사용자 엔티티를 다시 조회해 응답 DTO로 바꾼다.
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

    private AuthDTO.AuthResponse createAuthResponse(AppUser user) {
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

}

