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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Controller
public class MeetingController {

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final WemeetDataStore store;
    private final WemeetViewHelper viewHelper;

    public MeetingController(
            IWemeetViewService viewService,
            IApiAuthService authService,
            WemeetDataStore store,
            WemeetViewHelper viewHelper
    ) {
        this.viewService = viewService;
        this.authService = authService;
        this.store = store;
        this.viewHelper = viewHelper;
    }

    @GetMapping("/meetings/new")
    public String meetingForm(Model model, HttpSession session) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        List<FriendDTO.FriendSummary> friends = viewService.getFriends(currentUser.getId());
        populateMeetingFormModel(model, viewHelper.toProfile(currentUser), friends, extractFavoriteFriendIds(friends), null, false);
        return "meeting-form";
    }

    @GetMapping("/meetings/new1")
    public String meetingFormMock1(Model model) {
        UserDTO.UserProfile profile = viewService.getGuestUser();
        List<FriendDTO.FriendSummary> friends = viewService.getFriends(profile.id());
        populateMeetingFormModel(model, profile, friends, extractFavoriteFriendIds(friends), 1, true);
        return "meeting-form";
    }

    @GetMapping("/meetings/new2")
    public String meetingFormMock2(Model model) {
        UserDTO.UserProfile profile = viewService.getGuestUser();
        List<FriendDTO.FriendSummary> friends = viewService.getFriends(profile.id());
        populateMeetingFormModel(model, profile, friends, extractFavoriteFriendIds(friends), 2, true);
        return "meeting-form";
    }

    @GetMapping("/meetings/new3")
    public String meetingFormMock3(Model model) {
        UserDTO.UserProfile profile = viewService.getGuestUser();
        List<FriendDTO.FriendSummary> friends = viewService.getFriends(profile.id());
        populateMeetingFormModel(model, profile, friends, extractFavoriteFriendIds(friends), 3, true);
        return "meeting-form";
    }

    @GetMapping("/meetings/new4")
    public String meetingFormMock4(Model model) {
        UserDTO.UserProfile profile = viewService.getGuestUser();
        List<FriendDTO.FriendSummary> sampleFriends = List.of(
                new FriendDTO.FriendSummary("friend-201", "이영희", "@user456", "성수동 출발", "2026. 3. 1.", true),
                new FriendDTO.FriendSummary("friend-202", "박민수", "@user789", "잠실동 출발", "2026. 3. 3.", false)
        );
        populateMeetingFormModel(model, profile, sampleFriends, extractFavoriteFriendIds(sampleFriends), null, true);
        return "meeting-form";
    }

    @GetMapping("/search/results")
    public String quickResults(
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(required = false) List<String> friendIds,
            @RequestParam(defaultValue = "CENTER") String mode,
            @RequestParam(required = false) String anchorId,
            @RequestParam(defaultValue = "false") boolean guest,
            @RequestParam(required = false) String guestAddress,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = null;
        if (!guest) {
            currentUser = viewHelper.requireLoggedInUser(session);
        }
        populateRecommendationModel(model, guest ? "nearby" : "home", currentUser, category, friendIds, mode, anchorId, guest, guestAddress);
        return "search-results";
    }

    @PostMapping("/meetings/preview")
    public String previewResults(
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(required = false) List<String> friendIds,
            @RequestParam(defaultValue = "CENTER") String mode,
            @RequestParam(required = false) String anchorId,
            @RequestParam(defaultValue = "") String meetingName,
            @RequestParam(defaultValue = "") String meetingDate,
            @RequestParam(defaultValue = "") String meetingHour,
            @RequestParam(defaultValue = "") String meetingMinute,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        populateRecommendationModel(model, "create", currentUser, category, friendIds, mode, anchorId, false, null);
        model.addAttribute("meetingName", meetingName);
        model.addAttribute("meetingDate", meetingDate);
        model.addAttribute("meetingTime", composeMeetingTime(meetingHour, meetingMinute));
        return "search-results";
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
                request.participantIds()
        );
        return toMeetingResponse(meeting);
    }

    private void populateMeetingFormModel(
            Model model,
            UserDTO.UserProfile profile,
            List<FriendDTO.FriendSummary> friends,
            List<String> preselectedFriendIds,
            Integer redPlaceholderIndex,
            boolean hideShellNavigation
    ) {
        viewHelper.populateCommon(model, "create", false);
        List<FriendDTO.FriendSummary> favoriteFriends = friends.stream()
                .filter(FriendDTO.FriendSummary::favorite)
                .toList();
        model.addAttribute("profile", profile);
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("friends", friends);
        model.addAttribute("favoriteFriends", favoriteFriends);
        model.addAttribute("preselectedFriendIds", preselectedFriendIds);
        model.addAttribute("selectedCategory", "");
        model.addAttribute("selectedMode", RecommendationMode.CENTER.name());
        model.addAttribute("selectedAnchorId", profile.id());
        model.addAttribute("redPlaceholderIndex", redPlaceholderIndex);
        model.addAttribute("hideShellNavigation", hideShellNavigation);
    }

    private void populateRecommendationModel(
            Model model,
            String activeTab,
            AppUser currentUser,
            String category,
            List<String> friendIds,
            String mode,
            String anchorId,
            boolean guestMode,
            String guestAddress
    ) {
        viewHelper.populateCommon(model, activeTab, guestMode);
        UserDTO.UserProfile guestProfile = guestMode ? viewService.getGuestUser(guestAddress) : null;
        model.addAttribute("profile", guestMode ? guestProfile : viewHelper.toProfile(currentUser));
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("guestAddress", guestMode ? guestProfile.baseAddress() : "");
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedMode", RecommendationMode.from(mode).name());
        model.addAttribute("selectedAnchorId", anchorId == null || anchorId.isBlank()
                ? (guestMode ? guestProfile.id() : currentUser.getId())
                : anchorId);
        model.addAttribute("selectedFriendIds", guestMode ? List.of() : friendIds == null ? List.of() : friendIds);
        model.addAttribute("recommendation", guestMode
                ? viewService.buildGuestRecommendation(guestProfile.baseAddress(), category, mode, anchorId)
                : viewService.buildRecommendation(
                        currentUser.getId(),
                        category,
                        friendIds,
                        mode,
                        anchorId
                ));
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
}
