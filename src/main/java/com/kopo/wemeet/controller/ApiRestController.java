package com.kopo.wemeet.controller;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.entity.AppUser;
import com.kopo.wemeet.repository.WemeetDataStore;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IApiRecommendationService;
import com.kopo.wemeet.service.impl.NaverPlaceSearchService;
import com.kopo.wemeet.service.impl.NaverPlaceTagCatalog;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api")
public class ApiRestController {
    // 외부 클라이언트가 호출하는 REST API 진입점이다.
    // 화면용 컨트롤러와 분리해 두면 JSON 응답과 페이지 렌더링 책임을 나누기 쉽다.

    private final IApiAuthService authService;
    private final IApiRecommendationService recommendationService;
    private final NaverPlaceSearchService naverPlaceSearchService;
    private final NaverPlaceTagCatalog naverPlaceTagCatalog;
    private final WemeetDataStore store;

    public ApiRestController(
            IApiAuthService authService,
            IApiRecommendationService recommendationService,
            NaverPlaceSearchService naverPlaceSearchService,
            NaverPlaceTagCatalog naverPlaceTagCatalog,
            WemeetDataStore store
    ) {
        this.authService = authService;
        this.recommendationService = recommendationService;
        this.naverPlaceSearchService = naverPlaceSearchService;
        this.naverPlaceTagCatalog = naverPlaceTagCatalog;
        this.store = store;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/categories")
    public ApiDtos.CategoryResponse categories() {
        // 카테고리 목록은 추천 서비스가 기준 데이터를 관리하므로 그대로 위임한다.
        return recommendationService.categories();
    }

    @GetMapping("/place-tags")
    public ApiDtos.PlaceTagCatalogResponse placeTags() {
        return naverPlaceTagCatalog.catalogResponse();
    }

    @PostMapping("/auth/signup")
    public ApiDtos.AuthResponse signUp(@RequestBody ApiDtos.SignUpRequest request) {
        return authService.signUp(request);
    }

    @PostMapping("/auth/login")
    public ApiDtos.AuthResponse login(@RequestBody ApiDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/auth/password/reset-request")
    public ApiDtos.PasswordResetResponse createPasswordResetToken(@RequestBody ApiDtos.PasswordResetRequest request) {
        return authService.createPasswordResetToken(request);
    }

    @PostMapping("/auth/password/reset-confirm")
    public ApiDtos.PasswordResetResponse resetPassword(@RequestBody ApiDtos.PasswordResetConfirmRequest request) {
        return authService.resetPassword(request);
    }

    @GetMapping("/auth/email/available")
    public ApiDtos.EmailAvailabilityResponse emailAvailable(@RequestParam String email) {
        boolean available = authService.isEmailAvailable(email);
        return new ApiDtos.EmailAvailabilityResponse(
                available,
                available ? "사용 가능한 이메일입니다." : "이미 사용 중인 이메일입니다."
        );
    }

    @PostMapping("/auth/email/send-code")
    public ApiDtos.EmailVerificationSendResponse sendEmailVerificationCode(
            @RequestBody ApiDtos.EmailVerificationSendRequest request,
            HttpSession session
    ) {
        String normalizedEmail = request.email() == null ? "" : request.email().trim();
        String code = authService.createSignupEmailVerificationCode(normalizedEmail);
        session.setAttribute("SIGNUP_VERIFICATION_EMAIL", normalizedEmail);
        session.setAttribute("SIGNUP_VERIFICATION_CODE", code);
        session.removeAttribute("SIGNUP_VERIFIED_EMAIL");
        return new ApiDtos.EmailVerificationSendResponse(
                true,
                "인증코드를 전송했습니다. 메일 연동 전 단계라 화면에서 preview 코드를 같이 보여줍니다.",
                code
        );
    }

    @PostMapping("/auth/email/verify-code")
    public ApiDtos.EmailVerificationConfirmResponse verifyEmailVerificationCode(
            @RequestBody ApiDtos.EmailVerificationConfirmRequest request,
            HttpSession session
    ) {
        String normalizedEmail = request.email() == null ? "" : request.email().trim();
        String pendingEmail = (String) session.getAttribute("SIGNUP_VERIFICATION_EMAIL");
        String pendingCode = (String) session.getAttribute("SIGNUP_VERIFICATION_CODE");
        String inputCode = request.code() == null ? "" : request.code().trim();

        if (normalizedEmail.isBlank() || inputCode.isBlank()) {
            return new ApiDtos.EmailVerificationConfirmResponse(false, "이메일과 인증코드를 입력해주세요.");
        }
        if (!normalizedEmail.equalsIgnoreCase(pendingEmail) || pendingCode == null) {
            return new ApiDtos.EmailVerificationConfirmResponse(false, "먼저 해당 이메일로 인증코드를 전송해주세요.");
        }
        if (!pendingCode.equals(inputCode)) {
            return new ApiDtos.EmailVerificationConfirmResponse(false, "인증코드가 일치하지 않습니다.");
        }

        session.setAttribute("SIGNUP_VERIFIED_EMAIL", normalizedEmail);
        return new ApiDtos.EmailVerificationConfirmResponse(true, "이메일 인증이 완료되었습니다.");
    }

    @GetMapping("/me")
    public ApiDtos.UserResponse me(@RequestHeader("Authorization") String authorization) {
        // 토큰 검증과 사용자 조회는 인증 서비스가 담당한다.
        return authService.toUserResponse(authService.requireUser(authorization));
    }

    @PostMapping("/me/address")
    public ApiDtos.UserResponse updateAddress(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ApiDtos.AddressUpdateRequest request
    ) {
        // 로그인 사용자의 기본 출발지를 수정하면 이후 추천 계산의 출발점도 함께 바뀐다.
        AppUser requester = authService.requireUser(authorization);
        return authService.updateBaseAddress(requester, request.baseAddress());
    }

    @GetMapping("/friends")
    public List<ApiDtos.UserResponse> friends(@RequestHeader("Authorization") String authorization) {
        // 친구 정보는 현재 메모리 저장소에서 관리하는 관계 데이터를 기준으로 응답한다.
        AppUser requester = authService.requireUser(authorization);
        return store.listFriends(requester.getId()).stream().map(authService::toUserResponse).toList();
    }

    @PostMapping("/friends")
    public ApiDtos.UserResponse addFriend(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ApiDtos.FriendAddRequest request
    ) {
        // 친구 추가는 친구코드를 통해 양방향 관계를 생성한다.
        AppUser requester = authService.requireUser(authorization);
        return authService.toUserResponse(store.addFriendByCode(requester.getId(), request.friendCode()));
    }

    @GetMapping("/history")
    public List<ApiDtos.SearchHistoryResponse> history(@RequestHeader("Authorization") String authorization) {
        // 검색 기록은 사용자별로 최근 순서대로 반환한다.
        AppUser requester = authService.requireUser(authorization);
        return store.listHistory(requester.getId()).stream()
                .map(entry -> new ApiDtos.SearchHistoryResponse(
                        entry.query(),
                        entry.category(),
                        entry.searchedAt().toString()
                ))
                .toList();
    }

    @GetMapping("/meetings")
    public List<ApiDtos.MeetingResponse> meetings(@RequestHeader("Authorization") String authorization) {
        // 모임 조회는 현재 로그인 사용자가 참가자로 포함된 모임만 노출한다.
        AppUser requester = authService.requireUser(authorization);
        return store.listMeetingsForUser(requester.getId()).stream()
                .map(this::toMeetingResponse)
                .toList();
    }

    @PostMapping("/meetings")
    public ApiDtos.MeetingResponse createMeeting(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ApiDtos.MeetingCreateRequest request
    ) {
        // 날짜 문자열은 컨트롤러에서 먼저 검증해 서비스/저장소 쪽에는 정상 값만 넘긴다.
        AppUser requester = authService.requireUser(authorization);
        LocalDate meetingDate = parseMeetingDate(request.meetingDate());
        WemeetDataStore.MeetingRecord meeting = store.createMeeting(
                requester.getId(),
                request.title(),
                request.description(),
                meetingDate,
                request.category(),
                request.participantIds()
        );
        return toMeetingResponse(meeting);
    }

    @PostMapping("/recommendations")
    public ApiDtos.RecommendationResponse recommendations(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ApiDtos.RecommendationRequest request
    ) {
        // 추천 계산은 입력 조건이 많기 때문에 전용 서비스에 모두 위임한다.
        AppUser requester = authService.requireUser(authorization);
        return recommendationService.recommend(requester.getId(), request, authService);
    }

    @PostMapping("/places/search")
    public ApiDtos.PlaceSearchResponse searchPlaces(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ApiDtos.PlaceSearchRequest request
    ) {
        authService.requireUser(authorization);
        return naverPlaceSearchService.search(request);
    }

    @PostMapping("/public/places/search")
    public ApiDtos.PlaceSearchResponse publicPlaceSearch(@RequestBody ApiDtos.PlaceSearchRequest request) {
        return naverPlaceSearchService.search(request);
    }

    @PostMapping("/public/recommendations")
    public ApiDtos.RecommendationResponse publicRecommendations(@RequestBody ApiDtos.GuestRecommendationRequest request) {
        ApiDtos.UserResponse guestUser = createGuestUser(request.baseAddress());
        ApiDtos.RecommendationRequest recommendationRequest = new ApiDtos.RecommendationRequest(
                request.category(),
                List.of(),
                request.mode(),
                request.anchorParticipantId()
        );
        return recommendationService.recommendForGuest(guestUser, recommendationRequest);
    }

    private ApiDtos.MeetingResponse toMeetingResponse(WemeetDataStore.MeetingRecord meeting) {
        // 화면/API에서 바로 쓰기 쉽도록 host, participants 정보를 DTO 형태로 묶는다.
        WemeetDataStore.UserAccount host = store.findById(meeting.hostUserId()).orElseThrow();
        List<ApiDtos.UserResponse> participants = meeting.participantIds().stream()
                .map(id -> store.findById(id).orElseThrow())
                .map(authService::toUserResponse)
                .toList();

        return new ApiDtos.MeetingResponse(
                meeting.id(),
                meeting.title(),
                meeting.description(),
                meeting.meetingDate().toString(),
                meeting.category(),
                authService.toUserResponse(host),
                participants
        );
    }

    private LocalDate parseMeetingDate(String meetingDate) {
        try {
            return LocalDate.parse(meetingDate);
        } catch (DateTimeParseException exception) {
            // 입력 포맷 오류는 400으로 명확하게 돌려줘야 프론트에서도 처리하기 쉽다.
            throw new org.springframework.web.server.ResponseStatusException(BAD_REQUEST, "meetingDate must be ISO-8601 format (yyyy-MM-dd)");
        }
    }

    private ApiDtos.UserResponse createGuestUser(String baseAddress) {
        String normalizedBaseAddress = baseAddress == null || baseAddress.isBlank()
                ? "서울특별시 중구 명동길 74"
                : baseAddress.trim();
        return new ApiDtos.UserResponse(
                "guest-user",
                "게스트",
                "guest",
                "",
                "GUEST",
                normalizedBaseAddress
        );
    }
}
