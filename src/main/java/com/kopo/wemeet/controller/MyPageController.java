package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.MeetingDTO;
import com.kopo.wemeet.dto.RecommendationDTO;
import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IMeetingService;
import com.kopo.wemeet.service.IWemeetViewService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static com.kopo.wemeet.util.UiDefaults.DEFAULT_ROUTE_MODE;
import static com.kopo.wemeet.util.UiDefaults.TAB_PROFILE;

/**
 * MyPageController는 화면 요청과 API 요청을 받아 서비스 계층으로 위임하는 MVC 컨트롤러입니다.
 */
@Controller
@RequiredArgsConstructor
public class MyPageController {
    // 프로필, 계정 수정, 저장된 모임 결과 다시 보기를 담당하는 컨트롤러.

    private static final String PROFILE_EDIT_VERIFIED = "PROFILE_EDIT_VERIFIED";

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final WemeetViewHelper viewHelper;
    private final IMeetingService meetingService;
    private final ObjectMapper objectMapper;

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        List<MeetingDTO.CreatedMeeting> createdMeetings = enrichProfileMeetings(
                viewService.getCreatedMeetings(currentUser.getId()),
                currentUser.getId()
        );
        List<MeetingDTO.CreatedMeeting> participatingMeetings = enrichProfileMeetings(
                viewService.getParticipatingMeetings(currentUser.getId()),
                currentUser.getId()
        );
        viewHelper.populateCommon(model, TAB_PROFILE, false);
        model.addAttribute("profile", viewHelper.toProfile(
                currentUser,
                createdMeetings.size(),
                viewService.getFriends(currentUser.getId()).size(),
                participatingMeetings.size()
        ));
        model.addAttribute("meetingInvitations", viewService.getMeetingInvitations(currentUser.getId()));
        model.addAttribute("createdMeetings", createdMeetings);
        model.addAttribute("participatingMeetings", participatingMeetings);
        return "profile/index";
    }

    @GetMapping("/profile/meetings/results")
    public String createdMeetingResults(
            @RequestParam String meetingId,
            @RequestParam(defaultValue = DEFAULT_ROUTE_MODE) String routeMode,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        MeetingDTO.MeetingRecord meeting = meetingService.findMeetingForUser(currentUser.getId(), meetingId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Meeting not found"
                ));
        List<String> selectedFriendIds = meeting.participantIds().stream()
                .filter(participantId -> !participantId.equals(currentUser.getId()))
                .toList();

        viewHelper.populateCommon(model, TAB_PROFILE, false);
        model.addAttribute("profile", viewHelper.toProfile(currentUser));
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("guestAddress", "");
        model.addAttribute("selectedCategory", meeting.category());
        String savedMode = RecommendationMode.from(meeting.recommendationMode()).name();
        String savedAnchorId = meeting.anchorParticipantId() == null || meeting.anchorParticipantId().isBlank()
                ? currentUser.getId()
                : meeting.anchorParticipantId();
        model.addAttribute("selectedMode", savedMode);
        model.addAttribute("selectedAnchorId", savedAnchorId);
        model.addAttribute("selectedFriendIds", selectedFriendIds);
        model.addAttribute("meetingParticipantStatuses", meeting.participantStatuses());
        model.addAttribute("meetingCreationAvailable", false);
        String normalizedRouteMode = viewHelper.normalizeRouteMode(routeMode);
        model.addAttribute("selectedRouteMode", normalizedRouteMode);
        RecommendationDTO.RecommendationBundle savedRecommendation = deserializeRecommendationSnapshot(meeting);
        if (savedRecommendation != null) {
            model.addAttribute("recommendation", prioritizeSavedMeetingPlace(savedRecommendation, meeting));
            model.addAttribute("recommendationSnapshotNotice", "저장된 추천 결과를 보여주고 있습니다.");
            if (meeting.recommendationSnapshotExpiresAt() != null) {
                model.addAttribute("recommendationSnapshotExpiresOn", meeting.recommendationSnapshotExpiresAt().toLocalDate());
            }
        } else {
            model.addAttribute("recommendationSnapshotNotice", "저장된 추천 결과가 없어 최신 기준으로 다시 계산했습니다.");
            model.addAttribute("recommendation", viewService.buildRecommendation(
                    currentUser.getId(),
                    meeting.category(),
                    "",
                    selectedFriendIds,
                    savedMode,
                    savedAnchorId,
                    normalizedRouteMode,
                    ""
            ));
        }
        return "meeting/results";
    }

    @PostMapping("/profile/meetings/invitations/respond")
    public String respondMeetingInvitation(
            @RequestParam(defaultValue = "") String meetingId,
            @RequestParam(defaultValue = "") String action,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        boolean accept = "accept".equalsIgnoreCase(action);
        meetingService.respondMeetingInvitation(currentUser.getId(), meetingId, accept);
        redirectAttributes.addFlashAttribute(
                "profileNotice",
                accept ? "모임 초대를 수락했습니다." : "모임 초대를 거절했습니다."
        );
        return "redirect:/profile";
    }

    private RecommendationDTO.RecommendationBundle deserializeRecommendationSnapshot(MeetingDTO.MeetingRecord meeting) {
        if (meeting.recommendationSnapshotJson() == null || meeting.recommendationSnapshotJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(meeting.recommendationSnapshotJson(), RecommendationDTO.RecommendationBundle.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private RecommendationDTO.RecommendationBundle prioritizeSavedMeetingPlace(
            RecommendationDTO.RecommendationBundle snapshot,
            MeetingDTO.MeetingRecord meeting
    ) {
        if (snapshot.venues() == null || snapshot.venues().isEmpty()) {
            return snapshot;
        }

        int selectedVenueIndex = findSavedMeetingVenueIndex(snapshot.venues(), meeting);
        if (selectedVenueIndex <= 0) {
            return selectedVenueIndex == 0 ? copySnapshotWithSelectedVenueMapPoints(snapshot, snapshot.venues()) : snapshot;
        }

        List<RecommendationDTO.VenueOption> reorderedVenues = new ArrayList<>(snapshot.venues());
        RecommendationDTO.VenueOption selectedVenue = reorderedVenues.remove(selectedVenueIndex);
        reorderedVenues.add(0, selectedVenue);

        return copySnapshotWithSelectedVenueMapPoints(snapshot, reorderedVenues);
    }

    private int findSavedMeetingVenueIndex(
            List<RecommendationDTO.VenueOption> venues,
            MeetingDTO.MeetingRecord meeting
    ) {
        for (int index = 0; index < venues.size(); index++) {
            RecommendationDTO.VenueOption venue = venues.get(index);
            if (sameText(venue.name(), meeting.meetingPlaceName())
                    && (isBlank(meeting.meetingPlaceAddress()) || sameText(venue.description(), meeting.meetingPlaceAddress()))) {
                return index;
            }
        }
        for (int index = 0; index < venues.size(); index++) {
            if (sameText(venues.get(index).name(), meeting.meetingPlaceName())) {
                return index;
            }
        }
        return -1;
    }

    private RecommendationDTO.RecommendationBundle copySnapshotWithSelectedVenueMapPoints(
            RecommendationDTO.RecommendationBundle snapshot,
            List<RecommendationDTO.VenueOption> venues
    ) {
        RecommendationDTO.VenueOption selectedVenue = venues.get(0);
        RecommendationDTO.MidpointSummary midpoint = new RecommendationDTO.MidpointSummary(
                snapshot.midpoint().district(),
                snapshot.midpoint().station(),
                snapshot.midpoint().latitude(),
                snapshot.midpoint().longitude(),
                selectedVenue.averageMinutes(),
                selectedVenue.fairnessGap(),
                snapshot.midpoint().note()
        );

        List<RecommendationDTO.MapPoint> mapPoints = new ArrayList<>();
        if (snapshot.mapPoints() != null) {
            snapshot.mapPoints().stream()
                    .filter(point -> !"venue".equals(point.markerType()))
                    .forEach(mapPoints::add);
        }
        for (int index = 0; index < venues.size(); index++) {
            RecommendationDTO.VenueOption venue = venues.get(index);
            mapPoints.add(new RecommendationDTO.MapPoint(
                    "venue-" + index,
                    venue.name(),
                    venue.description(),
                    venue.latitude(),
                    venue.longitude(),
                    "venue",
                    index == 0
            ));
        }

        return new RecommendationDTO.RecommendationBundle(
                snapshot.category(),
                snapshot.participants(),
                midpoint,
                venues,
                mapPoints,
                snapshot.calculationMode(),
                snapshot.excludedParticipants()
        );
    }

    private boolean sameText(String left, String right) {
        return left != null && right != null && left.trim().equals(right.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<MeetingDTO.CreatedMeeting> enrichProfileMeetings(List<MeetingDTO.CreatedMeeting> meetings, String viewerUserId) {
        return meetings.stream()
                .map(meeting -> meetingService.findMeetingForUser(viewerUserId, meeting.id())
                        .map(record -> copyMeetingWithNaverMapUrl(meeting, buildMeetingNaverMapUrl(record, viewerUserId)))
                        .orElse(meeting))
                .toList();
    }

    private MeetingDTO.CreatedMeeting copyMeetingWithNaverMapUrl(MeetingDTO.CreatedMeeting meeting, String naverMapUrl) {
        return new MeetingDTO.CreatedMeeting(
                meeting.id(),
                meeting.title(),
                meeting.description(),
                meeting.dateLabel(),
                meeting.timeLabel(),
                meeting.category(),
                meeting.meetingPlaceName(),
                meeting.meetingPlaceAddress(),
                meeting.participantCount(),
                naverMapUrl
        );
    }

    private String buildMeetingNaverMapUrl(MeetingDTO.MeetingRecord meeting, String viewerUserId) {
        if (meeting.meetingPlaceName() == null || meeting.meetingPlaceName().isBlank()) {
            return "";
        }

        RecommendationDTO.RecommendationBundle snapshot = deserializeRecommendationSnapshot(meeting);
        RecommendationDTO.MapPoint originPoint = resolveMeetingOriginPoint(snapshot, viewerUserId);
        RecommendationDTO.VenueOption destinationVenue = resolveMeetingDestinationVenue(snapshot, meeting);
        if (originPoint == null || destinationVenue == null) {
            return "https://map.naver.com/p/search/" + org.springframework.web.util.UriUtils.encodePathSegment(
                    meeting.meetingPlaceName().trim(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        }

        return UriComponentsBuilder.fromUriString("https://map.naver.com/index.nhn")
                .queryParam("menu", "route")
                .queryParam("slng", originPoint.longitude())
                .queryParam("slat", originPoint.latitude())
                .queryParam("stext", firstNonBlank(originPoint.address(), originPoint.label(), "출발지"))
                .queryParam("elng", destinationVenue.longitude())
                .queryParam("elat", destinationVenue.latitude())
                .queryParam("etext", destinationVenue.name())
                .build()
                .toUriString();
    }

    private RecommendationDTO.MapPoint resolveMeetingOriginPoint(
            RecommendationDTO.RecommendationBundle snapshot,
            String viewerUserId
    ) {
        if (snapshot == null) {
            return null;
        }

        return snapshot.mapPoints().stream()
                .filter(point -> viewerUserId.equals(point.id()))
                .findFirst()
                .or(() -> snapshot.mapPoints().stream()
                        .filter(point -> "participant".equals(point.markerType()) || "anchor".equals(point.markerType()))
                        .findFirst())
                .orElse(null);
    }

    private RecommendationDTO.VenueOption resolveMeetingDestinationVenue(
            RecommendationDTO.RecommendationBundle snapshot,
            MeetingDTO.MeetingRecord meeting
    ) {
        if (snapshot == null) {
            return null;
        }

        return snapshot.venues().stream()
                .filter(venue -> venue.name() != null && venue.name().equals(meeting.meetingPlaceName()))
                .filter(venue -> meeting.meetingPlaceAddress() == null
                        || meeting.meetingPlaceAddress().isBlank()
                        || meeting.meetingPlaceAddress().equals(venue.description()))
                .findFirst()
                .or(() -> snapshot.venues().stream()
                        .filter(venue -> venue.name() != null && venue.name().equals(meeting.meetingPlaceName()))
                        .findFirst())
                .or(() -> snapshot.venues().stream().findFirst())
                .orElse(null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    @PostMapping("/profile/meetings/delete")
    public String deleteCreatedMeeting(
            @RequestParam String meetingId,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        meetingService.deleteMeetingCreatedByUser(currentUser.getId(), meetingId);
        redirectAttributes.addFlashAttribute("profileNotice", "모임이 삭제되었습니다.");
        return "redirect:/profile";
    }

    @GetMapping("/profile/verify-password")
    public String profilePasswordCheck(Model model, HttpSession session) {
        viewHelper.requireLoggedInUser(session);
        viewHelper.populateCommon(model, TAB_PROFILE, false);
        return "profile/password-check";
    }

    @PostMapping("/profile/verify-password")
    public String verifyProfilePassword(
            @RequestParam(defaultValue = "") String password,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        if (!authService.matchesPassword(currentUser, password)) {
            redirectAttributes.addFlashAttribute("profileVerifyPasswordError", "비밀번호가 올바르지 않습니다.");
            return "redirect:/profile/verify-password";
        }

        session.setAttribute(PROFILE_EDIT_VERIFIED, true);
        return "redirect:/profile/edit";
    }

    @GetMapping("/profile/edit")
    public String profileEdit(
            @RequestParam(defaultValue = "password") String editTab,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        if (!Boolean.TRUE.equals(session.getAttribute(PROFILE_EDIT_VERIFIED))) {
            return "redirect:/profile/verify-password";
        }

        viewHelper.populateCommon(model, TAB_PROFILE, false);
        model.addAttribute("profile", viewHelper.toProfile(currentUser));
        model.addAttribute("selectedProfileEditTab", "address".equalsIgnoreCase(editTab) ? "address" : "password");
        return "profile/edit";
    }

    @GetMapping("/profile/delete")
    public String profileDeleteConfirm(Model model, HttpSession session) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        if (!Boolean.TRUE.equals(session.getAttribute(PROFILE_EDIT_VERIFIED))) {
            return "redirect:/profile/verify-password";
        }

        viewHelper.populateCommon(model, TAB_PROFILE, false);
        model.addAttribute("profile", viewHelper.toProfile(currentUser));
        return "profile/delete-confirm";
    }

    @PostMapping("/profile/address")
    public String updateProfileAddress(
            @RequestParam(defaultValue = "") String baseAddress,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        authService.updateBaseAddress(currentUser, baseAddress);
        redirectAttributes.addFlashAttribute("profileNotice", "기본 출발지 주소가 수정되었습니다.");
        return "redirect:/profile";
    }

    @PostMapping("/profile/password")
    public String updateProfilePassword(
            @RequestParam(defaultValue = "") String newPassword,
            @RequestParam(defaultValue = "") String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        if (newPassword == null || newPassword.isBlank()) {
            redirectAttributes.addFlashAttribute("profilePasswordError", "새 비밀번호를 입력해주세요.");
            redirectAttributes.addAttribute("editTab", "password");
            return "redirect:/profile/edit";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("profilePasswordError", "비밀번호 확인이 일치하지 않습니다.");
            redirectAttributes.addAttribute("editTab", "password");
            return "redirect:/profile/edit";
        }

        authService.resetPasswordForUser(currentUser.getId(), newPassword);
        redirectAttributes.addFlashAttribute("profilePasswordNotice", "비밀번호가 변경되었습니다.");
        redirectAttributes.addAttribute("editTab", "password");
        return "redirect:/profile/edit";
    }

    @PostMapping("/profile/delete")
    public String deleteProfile(
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        authService.deleteUserAccount(currentUser.getId());
        session.invalidate();
        redirectAttributes.addFlashAttribute("accountDeletedNotice", "회원탈퇴가 완료되었습니다.");
        return "redirect:/login";
    }
}

