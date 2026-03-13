package com.kopo.wemeet.controller;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.entity.AppUser;
import com.kopo.wemeet.repository.InMemoryWemeetStore;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IApiRecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api")
public class ApiRestController {

    private final IApiAuthService authService;
    private final IApiRecommendationService recommendationService;
    private final InMemoryWemeetStore store;

    public ApiRestController(
            IApiAuthService authService,
            IApiRecommendationService recommendationService,
            InMemoryWemeetStore store
    ) {
        this.authService = authService;
        this.recommendationService = recommendationService;
        this.store = store;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/categories")
    public ApiDtos.CategoryResponse categories() {
        return recommendationService.categories();
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

    @GetMapping("/me")
    public ApiDtos.UserResponse me(@RequestHeader("Authorization") String authorization) {
        return authService.toUserResponse(authService.requireUser(authorization));
    }

    @PostMapping("/me/address")
    public ApiDtos.UserResponse updateAddress(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ApiDtos.AddressUpdateRequest request
    ) {
        AppUser requester = authService.requireUser(authorization);
        return authService.updateBaseAddress(requester, request.baseAddress());
    }

    @GetMapping("/friends")
    public List<ApiDtos.UserResponse> friends(@RequestHeader("Authorization") String authorization) {
        AppUser requester = authService.requireUser(authorization);
        return store.listFriends(requester.getId()).stream().map(authService::toUserResponse).toList();
    }

    @PostMapping("/friends")
    public ApiDtos.UserResponse addFriend(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ApiDtos.FriendAddRequest request
    ) {
        AppUser requester = authService.requireUser(authorization);
        return authService.toUserResponse(store.addFriendByCode(requester.getId(), request.friendCode()));
    }

    @GetMapping("/history")
    public List<ApiDtos.SearchHistoryResponse> history(@RequestHeader("Authorization") String authorization) {
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
        AppUser requester = authService.requireUser(authorization);
        LocalDate meetingDate = parseMeetingDate(request.meetingDate());
        InMemoryWemeetStore.MeetingRecord meeting = store.createMeeting(
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
        AppUser requester = authService.requireUser(authorization);
        return recommendationService.recommend(requester.getId(), request, authService);
    }

    private ApiDtos.MeetingResponse toMeetingResponse(InMemoryWemeetStore.MeetingRecord meeting) {
        InMemoryWemeetStore.UserAccount host = store.findById(meeting.hostUserId()).orElseThrow();
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
            throw new org.springframework.web.server.ResponseStatusException(BAD_REQUEST, "meetingDate must be ISO-8601 format (yyyy-MM-dd)");
        }
    }
}
