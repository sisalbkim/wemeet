package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.*;
import com.kopo.wemeet.repository.WemeetDataStore;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IWemeetViewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Controller
public class MeetingController {
    // 모임 생성 폼, 추천 결과 미리보기, 모임 저장 API를 처리하는 컨트롤러.

    private static final String MEETING_PREVIEW_SNAPSHOTS = "MEETING_PREVIEW_SNAPSHOTS";

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final WemeetDataStore store;
    private final WemeetViewHelper viewHelper;
    private final ObjectMapper objectMapper;

    public MeetingController(
            IWemeetViewService viewService,
            IApiAuthService authService,
            WemeetDataStore store,
            WemeetViewHelper viewHelper,
            ObjectMapper objectMapper
    ) {
        this.viewService = viewService;
        this.authService = authService;
        this.store = store;
        this.viewHelper = viewHelper;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/meetings/new")
    public String meetingForm(Model model, HttpSession session) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        List<FriendDTO.FriendSummary> friends = viewService.getFriends(currentUser.getId());
        populateMeetingFormModel(model, viewHelper.toProfile(currentUser), friends, extractFavoriteFriendIds(friends));
        return "meeting/form";
    }

    @GetMapping("/search/results")
    public String quickResults(
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(required = false) List<String> friendIds,
            @RequestParam(defaultValue = "CENTER") String mode,
            @RequestParam(required = false) String anchorId,
            @RequestParam(defaultValue = "false") boolean guest,
            @RequestParam(required = false) String guestAddress,
            @RequestParam(defaultValue = "car") String routeMode,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = null;
        if (!guest) {
            currentUser = viewHelper.requireLoggedInUser(session);
        }
        String normalizedRouteMode = viewHelper.normalizeRouteMode(routeMode);
        populateRecommendationModel(model, guest ? "nearby" : "home", currentUser, category, friendIds, mode, anchorId, guest, guestAddress, normalizedRouteMode);
        model.addAttribute("selectedRouteMode", normalizedRouteMode);
        return "meeting/results";
    }

    @PostMapping("/meetings/preview")
    public String previewResults(
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(required = false) List<String> friendIds,
            @RequestParam(defaultValue = "CENTER") String mode,
            @RequestParam(required = false) String anchorId,
            @RequestParam(defaultValue = "") String meetingName,
            @RequestParam(defaultValue = "") String meetingDescription,
            @RequestParam(defaultValue = "") String meetingDate,
            @RequestParam(defaultValue = "") String meetingHour,
            @RequestParam(defaultValue = "") String meetingMinute,
            @RequestParam(defaultValue = "car") String routeMode,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        String normalizedRouteMode = viewHelper.normalizeRouteMode(routeMode);
        RecommendationDTO.RecommendationBundle recommendation = populateRecommendationModel(
                model,
                "create",
                currentUser,
                category,
                friendIds,
                mode,
                anchorId,
                false,
                null,
                normalizedRouteMode
        );
        model.addAttribute("meetingName", meetingName);
        model.addAttribute("meetingDescription", meetingDescription);
        model.addAttribute("meetingDate", meetingDate);
        model.addAttribute("meetingTime", composeMeetingTime(meetingHour, meetingMinute));
        model.addAttribute("meetingCreationAvailable", true);
        model.addAttribute("meetingPreviewKey", rememberMeetingPreview(session, recommendation));
        model.addAttribute("selectedRouteMode", normalizedRouteMode);
        return "meeting/results";
    }

    @PostMapping("/meetings")
    public String createMeeting(
            @RequestParam(defaultValue = "") String meetingName,
            @RequestParam(defaultValue = "") String meetingDescription,
            @RequestParam(defaultValue = "") String meetingDate,
            @RequestParam(defaultValue = "") String meetingTime,
            @RequestParam(defaultValue = "") String meetingPlaceName,
            @RequestParam(defaultValue = "") String meetingPlaceAddress,
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(defaultValue = "CENTER") String recommendationMode,
            @RequestParam(defaultValue = "") String anchorId,
            @RequestParam(defaultValue = "car") String routeMode,
            @RequestParam(defaultValue = "") String meetingPreviewKey,
            @RequestParam(required = false) List<String> friendIds,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        if (meetingName == null || meetingName.isBlank() || meetingDate == null || meetingDate.isBlank()) {
            RecommendationDTO.RecommendationBundle recommendation = findMeetingPreview(session, meetingPreviewKey);
            if (recommendation != null) {
                return renderMeetingCreationError(
                        model,
                        currentUser,
                        recommendation,
                        category,
                        recommendationMode,
                        anchorId,
                        routeMode,
                        friendIds,
                        meetingName,
                        meetingDescription,
                        meetingDate,
                        meetingTime,
                        meetingPreviewKey,
                        "모임 이름과 날짜를 입력해주세요."
                );
            }

            redirectAttributes.addFlashAttribute("meetingCreateError", "추천 결과를 다시 불러온 뒤 저장해주세요.");
            return "redirect:/meetings/new";
        }

        LocalDate parsedMeetingDate = parseMeetingDate(meetingDate);
        String snapshotJson = consumeMeetingPreview(session, meetingPreviewKey);
        LocalDateTime snapshotExpiresAt = snapshotJson == null
                ? null
                : calculateSnapshotExpiry(parsedMeetingDate);

        store.createMeeting(
                currentUser.getId(),
                meetingName,
                meetingDescription,
                parsedMeetingDate,
                parseMeetingTime(meetingTime),
                category,
                meetingPlaceName,
                meetingPlaceAddress,
                recommendationMode,
                anchorId,
                snapshotJson,
                snapshotExpiresAt,
                friendIds == null ? List.of() : friendIds
        );
        redirectAttributes.addFlashAttribute("profileNotice", "모임이 생성되었습니다.");
        return "redirect:/profile";
    }

    @ResponseBody
    @GetMapping("/api/meetings")
    public List<MeetingDTO.MeetingResponse> apiMeetings(@RequestHeader("Authorization") String authorization) {
        AppUser requester = authService.requireUser(authorization);
        return store.listMeetingsForUser(requester.getId()).stream()
                .map(this::toMeetingResponse)
                .toList();
    }

    @ResponseBody
    @PostMapping("/api/meetings")
    public MeetingDTO.MeetingResponse apiCreateMeeting(
            @RequestHeader("Authorization") String authorization,
            @RequestBody MeetingDTO.MeetingCreateRequest request
    ) {
        AppUser requester = authService.requireUser(authorization);
        LocalDate meetingDate = parseMeetingDate(request.meetingDate());
        LocalTime meetingTime = parseMeetingTime(request.meetingTime());
        WemeetDataStore.MeetingRecord meeting = store.createMeeting(
                requester.getId(),
                request.title(),
                request.description(),
                meetingDate,
                meetingTime,
                request.category(),
                request.meetingPlaceName(),
                request.meetingPlaceAddress(),
                request.participantIds()
        );
        return toMeetingResponse(meeting);
    }

    private void populateMeetingFormModel(
            Model model,
            UserDTO.UserProfile profile,
            List<FriendDTO.FriendSummary> friends,
            List<String> preselectedFriendIds
    ) {
        viewHelper.populateCommon(model, "create", false);
        List<FriendDTO.FriendSummary> favoriteFriends = friends.stream()
                .filter(FriendDTO.FriendSummary::favorite)
                .toList();
        model.addAttribute("profile", profile);
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("friends", friends);
        model.addAttribute("favoriteFriends", favoriteFriends);
        model.addAttribute("preselectedFriendIds", preselectedFriendIds);
        model.addAttribute("selectedCategory", "");
        model.addAttribute("selectedMode", RecommendationMode.CENTER.name());
        model.addAttribute("selectedAnchorId", profile.id());
        model.addAttribute("selectedRouteMode", "car");
    }

    private RecommendationDTO.RecommendationBundle populateRecommendationModel(
            Model model,
            String activeTab,
            AppUser currentUser,
            String category,
            List<String> friendIds,
            String mode,
            String anchorId,
            boolean guestMode,
            String guestAddress,
            String routeMode
    ) {
        viewHelper.populateCommon(model, activeTab, guestMode);
        UserDTO.UserProfile guestProfile = guestMode ? viewService.getGuestUser(guestAddress) : null;
        RecommendationDTO.RecommendationBundle recommendation = guestMode
                ? viewService.buildGuestRecommendation(guestProfile.baseAddress(), category, mode, anchorId, routeMode)
                : viewService.buildRecommendation(
                        currentUser.getId(),
                        category,
                        friendIds,
                        mode,
                        anchorId,
                        routeMode
                );
        model.addAttribute("profile", guestMode ? guestProfile : viewHelper.toProfile(currentUser));
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("guestAddress", guestMode ? guestProfile.baseAddress() : "");
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedMode", RecommendationMode.from(mode).name());
        model.addAttribute("selectedAnchorId", anchorId == null || anchorId.isBlank()
                ? (guestMode ? guestProfile.id() : currentUser.getId())
                : anchorId);
        model.addAttribute("selectedFriendIds", guestMode ? List.of() : friendIds == null ? List.of() : friendIds);
        model.addAttribute("meetingCreationAvailable", false);
        model.addAttribute("selectedRouteMode", routeMode);
        model.addAttribute("recommendation", recommendation);
        return recommendation;
    }

    private String rememberMeetingPreview(HttpSession session, RecommendationDTO.RecommendationBundle recommendation) {
        if (session == null) {
            return "";
        }

        try {
            String previewKey = UUID.randomUUID().toString();
            Map<String, String> previews = meetingPreviewStore(session);
            previews.put(previewKey, objectMapper.writeValueAsString(recommendation));
            while (previews.size() > 5) {
                String oldestKey = previews.keySet().iterator().next();
                previews.remove(oldestKey);
            }
            session.setAttribute(MEETING_PREVIEW_SNAPSHOTS, previews);
            return previewKey;
        } catch (Exception exception) {
            return "";
        }
    }

    private String consumeMeetingPreview(HttpSession session, String previewKey) {
        if (session == null || previewKey == null || previewKey.isBlank()) {
            return null;
        }

        Map<String, String> previews = meetingPreviewStore(session);
        String snapshotJson = previews.remove(previewKey);
        session.setAttribute(MEETING_PREVIEW_SNAPSHOTS, previews);
        return snapshotJson == null || snapshotJson.isBlank() ? null : snapshotJson;
    }

    private RecommendationDTO.RecommendationBundle findMeetingPreview(HttpSession session, String previewKey) {
        if (session == null || previewKey == null || previewKey.isBlank()) {
            return null;
        }

        String snapshotJson = meetingPreviewStore(session).get(previewKey);
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(snapshotJson, RecommendationDTO.RecommendationBundle.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private String renderMeetingCreationError(
            Model model,
            AppUser currentUser,
            RecommendationDTO.RecommendationBundle recommendation,
            String category,
            String recommendationMode,
            String anchorId,
            String routeMode,
            List<String> friendIds,
            String meetingName,
            String meetingDescription,
            String meetingDate,
            String meetingTime,
            String meetingPreviewKey,
            String errorMessage
    ) {
        viewHelper.populateCommon(model, "create", false);
        model.addAttribute("profile", viewHelper.toProfile(currentUser));
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("guestAddress", "");
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedMode", RecommendationMode.from(recommendationMode).name());
        model.addAttribute("selectedAnchorId", anchorId == null || anchorId.isBlank() ? currentUser.getId() : anchorId);
        model.addAttribute("selectedFriendIds", friendIds == null ? List.of() : friendIds);
        model.addAttribute("meetingCreationAvailable", true);
        model.addAttribute("selectedRouteMode", viewHelper.normalizeRouteMode(routeMode));
        model.addAttribute("recommendation", recommendation);
        model.addAttribute("meetingName", meetingName);
        model.addAttribute("meetingDescription", meetingDescription);
        model.addAttribute("meetingDate", meetingDate);
        model.addAttribute("meetingTime", meetingTime);
        model.addAttribute("meetingPreviewKey", meetingPreviewKey);
        model.addAttribute("meetingCreateError", errorMessage);
        return "meeting/results";
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> meetingPreviewStore(HttpSession session) {
        Object cached = session.getAttribute(MEETING_PREVIEW_SNAPSHOTS);
        if (cached instanceof Map<?, ?> cachedMap) {
            Map<String, String> previews = new LinkedHashMap<>();
            cachedMap.forEach((key, value) -> {
                if (key instanceof String stringKey && value instanceof String stringValue) {
                    previews.put(stringKey, stringValue);
                }
            });
            return previews;
        }
        return new LinkedHashMap<>();
    }

    private MeetingDTO.MeetingResponse toMeetingResponse(WemeetDataStore.MeetingRecord meeting) {
        WemeetDataStore.UserAccount host = store.findById(meeting.hostUserId()).orElseThrow();
        List<UserDTO.UserResponse> participants = meeting.participantIds().stream()
                .map(id -> store.findById(id).orElseThrow())
                .map(authService::toUserResponse)
                .toList();

        return new MeetingDTO.MeetingResponse(
                meeting.id(),
                meeting.title(),
                meeting.description(),
                meeting.meetingDate().toString(),
                meeting.meetingTime() == null ? "" : meeting.meetingTime().toString(),
                meeting.category(),
                meeting.meetingPlaceName(),
                meeting.meetingPlaceAddress(),
                authService.toUserResponse(host),
                participants
        );
    }

    private List<String> extractFavoriteFriendIds(List<FriendDTO.FriendSummary> friends) {
        return friends.stream()
                .filter(FriendDTO.FriendSummary::favorite)
                .map(FriendDTO.FriendSummary::id)
                .toList();
    }

    private String composeMeetingTime(String meetingHour, String meetingMinute) {
        String hour = meetingHour == null ? "" : meetingHour.trim();
        if (hour.isBlank()) {
            return "";
        }
        String minute = meetingMinute == null || meetingMinute.isBlank() ? "00" : meetingMinute.trim();
        return hour + ":" + minute;
    }

    private LocalDate parseMeetingDate(String meetingDate) {
        try {
            return LocalDate.parse(meetingDate);
        } catch (DateTimeParseException exception) {
            throw new org.springframework.web.server.ResponseStatusException(BAD_REQUEST, "meetingDate must be ISO-8601 format (yyyy-MM-dd)");
        }
    }

    private LocalTime parseMeetingTime(String meetingTime) {
        if (meetingTime == null || meetingTime.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(meetingTime);
        } catch (DateTimeParseException exception) {
            throw new org.springframework.web.server.ResponseStatusException(BAD_REQUEST, "meetingTime must be ISO-8601 format (HH:mm)");
        }
    }

    private LocalDateTime calculateSnapshotExpiry(LocalDate meetingDate) {
        // LocalTime.MAX는 DB 저장 시 다음 날 00:00:00으로 반올림될 수 있어 초 단위로 고정한다.
        return meetingDate.plusDays(7).atTime(23, 59, 59);
    }

}
