package com.kopo.wemeet.api;

import com.kopo.wemeet.auth.AppUser;
import com.kopo.wemeet.auth.AppUserRepository;
import com.kopo.wemeet.auth.PasswordResetToken;
import com.kopo.wemeet.auth.PasswordResetTokenRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class ApiAuthService {

    private final AppUserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final InMemoryWemeetStore store;
    private final Map<String, String> sessionsByToken = new ConcurrentHashMap<>();

    public ApiAuthService(
            AppUserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            InMemoryWemeetStore store
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.store = store;
    }

    public ApiDtos.AuthResponse signUp(ApiDtos.SignUpRequest request) {
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
        store.syncUserSnapshot(user.getId(), user.getNickname(), user.getLoginId(), user.getFriendCode(), user.getBaseAddress());

        String token = createSession(user.getId());
        return new ApiDtos.AuthResponse(token, toUserResponse(user));
    }

    public ApiDtos.AuthResponse login(ApiDtos.LoginRequest request) {
        AppUser user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }

        store.syncUserSnapshot(user.getId(), user.getNickname(), user.getLoginId(), user.getFriendCode(), user.getBaseAddress());

        String token = createSession(user.getId());
        return new ApiDtos.AuthResponse(token, toUserResponse(user));
    }

    public ApiDtos.PasswordResetResponse createPasswordResetToken(ApiDtos.PasswordResetRequest request) {
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
    public ApiDtos.PasswordResetResponse resetPassword(ApiDtos.PasswordResetConfirmRequest request) {
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

    public AppUser requireUser(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authorization header must use Bearer token");
        }
        String userId = sessionsByToken.get(authorizationHeader.substring(7));
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid access token");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "User not found"));
    }

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

    public ApiDtos.UserResponse toUserResponse(InMemoryWemeetStore.UserAccount user) {
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
        if (request.loginId() == null || request.loginId().isBlank()
                || request.password() == null || request.password().isBlank()
                || request.email() == null || request.email().isBlank()
                || request.nickname() == null || request.nickname().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "nickname, loginId, password, and email are required");
        }
    }

    private String createSession(String userId) {
        String token = UUID.randomUUID().toString();
        sessionsByToken.put(token, userId);
        return token;
    }

    private String generateFriendCode(String loginId) {
        String base = loginId.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return (base.isBlank() ? "FRIEND" : base) + suffix;
    }

    private String hashToken(String rawToken) {
        try {
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
