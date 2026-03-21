package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.entity.AppUser;
import com.kopo.wemeet.entity.PasswordResetToken;
import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.PasswordResetTokenRepository;
import com.kopo.wemeet.repository.SessionTokenStore;
import com.kopo.wemeet.repository.WemeetDataStore;
import com.kopo.wemeet.service.IApiAuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class ApiAuthService implements IApiAuthService {
    // 회원가입, 로그인, 비밀번호 재설정처럼 인증과 계정 관리에 관한 핵심 로직을 모아 둔 서비스다.

    private final AppUserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenStore sessionTokenStore;

    public ApiAuthService(
            AppUserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            SessionTokenStore sessionTokenStore
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionTokenStore = sessionTokenStore;
    }

    @Override
    public ApiDtos.AuthResponse signUp(ApiDtos.SignUpRequest request) {
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

        String token = createSession(user.getId());
        return new ApiDtos.AuthResponse(token, toUserResponse(user));
    }

    @Override
    public ApiDtos.AuthResponse login(ApiDtos.LoginRequest request) {
        // 로그인은 아이디로 사용자를 찾은 뒤 비밀번호 해시를 비교한다.
        AppUser user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }

        String token = createSession(user.getId());
        return new ApiDtos.AuthResponse(token, toUserResponse(user));
    }

    @Override
    public ApiDtos.PasswordResetResponse createPasswordResetToken(ApiDtos.PasswordResetRequest request) {
        // 실제 메일 발송 전 단계라서 화면 시연용으로 raw token을 preview 형태로 함께 돌려준다.
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email is required");
        }

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

        return new ApiDtos.PasswordResetResponse(
                "비밀번호 재설정 토큰이 생성되었습니다. 메일 연동 전 단계라 preview 값을 같이 반환합니다.",
                rawToken
        );
    }

    @Transactional
    @Override
    public ApiDtos.PasswordResetResponse resetPassword(ApiDtos.PasswordResetConfirmRequest request) {
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

        return new ApiDtos.PasswordResetResponse("비밀번호가 변경되었습니다.", null);
    }

    @Transactional
    @Override
    public ApiDtos.UserResponse updateBaseAddress(AppUser user, String baseAddress) {
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
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authorization header must use Bearer token");
        }
        String userId = sessionTokenStore.findUserId(authorizationHeader.substring(7)).orElse(null);
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid access token");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "User not found"));
    }

    @Override
    public ApiDtos.UserResponse toUserResponse(AppUser user) {
        return new ApiDtos.UserResponse(
                user.getId(),
                user.getNickname(),
                user.getLoginId(),
                user.getEmail(),
                user.getFriendCode(),
                user.getBaseAddress()
        );
    }

    @Override
    public ApiDtos.UserResponse toUserResponse(WemeetDataStore.UserAccount user) {
        AppUser persistentUser = userRepository.findById(user.id())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found: " + user.id()));
        return new ApiDtos.UserResponse(
                persistentUser.getId(),
                persistentUser.getNickname(),
                persistentUser.getLoginId(),
                persistentUser.getEmail(),
                persistentUser.getFriendCode(),
                persistentUser.getBaseAddress()
        );
    }

    private void validateSignupRequest(ApiDtos.SignUpRequest request) {
        // 수업용 예제에서는 필수값 검증을 서비스에서 먼저 처리한다.
        if (request.loginId() == null || request.loginId().isBlank()
                || request.password() == null || request.password().isBlank()
                || request.email() == null || request.email().isBlank()
                || request.nickname() == null || request.nickname().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "nickname, loginId, password, and email are required");
        }
    }

    private String createSession(String userId) {
        String token = UUID.randomUUID().toString();
        // 세션 저장 구현은 Redis 또는 메모리 fallback 중 현재 설정에 맞는 쪽이 선택된다.
        sessionTokenStore.store(token, userId);
        return token;
    }

    private String generateFriendCode(String loginId) {
        // 친구코드는 사람이 입력하기 쉬운 형태를 위해 로그인 ID + 짧은 난수 조합으로 만든다.
        String base = loginId.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return (base.isBlank() ? "FRIEND" : base) + suffix;
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
