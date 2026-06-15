package com.kopo.wemeet.controller;

import com.kopo.wemeet.dto.FriendDTO;
import com.kopo.wemeet.dto.MeetingDTO;
import com.kopo.wemeet.dto.RecommendationDTO;
import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IMeetingService;
import com.kopo.wemeet.service.IWemeetViewService;
import com.kopo.wemeet.service.impl.MeetingPreviewSessionService;
import com.kopo.wemeet.util.WemeetViewHelper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static com.kopo.wemeet.util.UiDefaults.DEFAULT_CATEGORY;
import static com.kopo.wemeet.util.UiDefaults.DEFAULT_RECOMMENDATION_MODE;
import static com.kopo.wemeet.util.UiDefaults.DEFAULT_ROUTE_MODE;
import static com.kopo.wemeet.util.UiDefaults.TAB_CREATE;
import static com.kopo.wemeet.util.UiDefaults.TAB_HOME;
import static com.kopo.wemeet.util.UiDefaults.TAB_NEARBY;

@Controller
@RequiredArgsConstructor
public class MeetingViewController {
    // 모임 생성 화면과 추천 결과 미리보기 흐름을 담당한다.

    private final IWemeetViewService viewService;
    private final IMeetingService meetingService;
    private final WemeetViewHelper viewHelper;
    private final MeetingPreviewSessionService meetingPreviewSessionService;

    @GetMapping("/meetings/new")
    public String meetingForm(Model model, HttpSession session) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        List<FriendDTO.FriendSummary> friends = viewService.getFriends(currentUser.getId());
        populateMeetingFormModel(model, viewHelper.toProfile(currentUser), friends, extractFavoriteFriendIds(friends));
        return "meeting/form";
    }

    @GetMapping("/search/results")
    public String quickResults(
            @RequestParam(defaultValue = DEFAULT_CATEGORY) String category,
            @RequestParam(defaultValue = "") String detailKeyword,
            @RequestParam(required = false) List<String> friendIds,
            @RequestParam(defaultValue = DEFAULT_RECOMMENDATION_MODE) String mode,
            @RequestParam(required = false) String anchorId,
            @RequestParam(defaultValue = "false") boolean guest,
            @RequestParam(required = false) String guestAddress,
            @RequestParam(defaultValue = DEFAULT_ROUTE_MODE) String routeMode,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = null;
        if (!guest) {
            currentUser = viewHelper.requireLoggedInUser(session);
        }
        String normalizedRouteMode = viewHelper.normalizeRouteMode(routeMode);
        populateRecommendationModel(model, guest ? TAB_NEARBY : TAB_HOME, currentUser, category, detailKeyword, friendIds, mode, anchorId, guest, guestAddress, normalizedRouteMode);
        model.addAttribute("selectedRouteMode", normalizedRouteMode);
        return "meeting/results";
    }

    @PostMapping("/meetings/preview")
    public String previewResults(
            @RequestParam(defaultValue = DEFAULT_CATEGORY) String category,
            @RequestParam(defaultValue = "") String detailKeyword,
            @RequestParam(required = false) List<String> friendIds,
            @RequestParam(defaultValue = DEFAULT_RECOMMENDATION_MODE) String mode,
            @RequestParam(required = false) String anchorId,
            @RequestParam(defaultValue = "") String meetingName,
            @RequestParam(defaultValue = "") String meetingDescription,
            @RequestParam(defaultValue = "") String meetingDate,
            @RequestParam(defaultValue = "") String meetingHour,
            @RequestParam(defaultValue = "") String meetingMinute,
            @RequestParam(defaultValue = DEFAULT_ROUTE_MODE) String routeMode,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        String normalizedRouteMode = viewHelper.normalizeRouteMode(routeMode);
        RecommendationDTO.RecommendationBundle recommendation = populateRecommendationModel(
                model,
                TAB_CREATE,
                currentUser,
                category,
                detailKeyword,
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
        model.addAttribute("meetingPreviewKey", meetingPreviewSessionService.rememberPreview(session, recommendation));
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
            @RequestParam(defaultValue = DEFAULT_CATEGORY) String category,
            @RequestParam(defaultValue = DEFAULT_RECOMMENDATION_MODE) String recommendationMode,
            @RequestParam(defaultValue = "") String anchorId,
            @RequestParam(defaultValue = DEFAULT_ROUTE_MODE) String routeMode,
            @RequestParam(defaultValue = "") String meetingPreviewKey,
            @RequestParam(required = false) List<String> friendIds,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        if (meetingName == null || meetingName.isBlank() || meetingDate == null || meetingDate.isBlank()) {
            RecommendationDTO.RecommendationBundle recommendation = meetingPreviewSessionService.findPreview(session, meetingPreviewKey);
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
        String snapshotJson = meetingPreviewSessionService.consumePreview(session, meetingPreviewKey);
        LocalDateTime snapshotExpiresAt = snapshotJson == null
                ? null
                : calculateSnapshotExpiry(parsedMeetingDate);

        meetingService.createMeeting(
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

    private void populateMeetingFormModel(
            Model model,
            UserDTO.UserProfile profile,
            List<FriendDTO.FriendSummary> friends,
            List<String> preselectedFriendIds
    ) {
        viewHelper.populateCommon(model, TAB_CREATE, false);
        List<FriendDTO.FriendSummary> favoriteFriends = friends.stream()
                .filter(FriendDTO.FriendSummary::favorite)
                .toList();
        model.addAttribute("profile", profile);
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("friends", friends);
        model.addAttribute("favoriteFriends", favoriteFriends);
        model.addAttribute("preselectedFriendIds", preselectedFriendIds);
        model.addAttribute("selectedCategory", "");
        model.addAttribute("detailKeyword", "");
        model.addAttribute("selectedMode", DEFAULT_RECOMMENDATION_MODE);
        model.addAttribute("selectedAnchorId", profile.id());
        model.addAttribute("selectedRouteMode", DEFAULT_ROUTE_MODE);
    }

    private RecommendationDTO.RecommendationBundle populateRecommendationModel(
            Model model,
            String activeTab,
            AppUser currentUser,
            String category,
            String detailKeyword,
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
                ? viewService.buildGuestRecommendation(guestProfile.baseAddress(), category, detailKeyword, mode, anchorId, routeMode)
                : viewService.buildRecommendation(
                        currentUser.getId(),
                        category,
                        detailKeyword,
                        friendIds,
                        mode,
                        anchorId,
                        routeMode
                );
        model.addAttribute("profile", guestMode ? guestProfile : viewHelper.toProfile(currentUser));
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("guestAddress", guestMode ? guestProfile.baseAddress() : "");
        model.addAttribute("selectedCategory", category);
        model.addAttribute("detailKeyword", normalizeOptionalText(detailKeyword));
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

    private String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
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
        viewHelper.populateCommon(model, TAB_CREATE, false);
        model.addAttribute("profile", viewHelper.toProfile(currentUser));
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("guestAddress", "");
        model.addAttribute("selectedCategory", category);
        model.addAttribute("detailKeyword", "");
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
            throw new ResponseStatusException(BAD_REQUEST, "meetingDate must be ISO-8601 format (yyyy-MM-dd)");
        }
    }

    private LocalTime parseMeetingTime(String meetingTime) {
        if (meetingTime == null || meetingTime.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(meetingTime);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "meetingTime must be ISO-8601 format (HH:mm)");
        }
    }

    private LocalDateTime calculateSnapshotExpiry(LocalDate meetingDate) {
        return meetingDate.plusDays(7).atTime(23, 59, 59);
    }
}
