package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.MeetingDTO;
import com.kopo.wemeet.dto.RecommendationDTO;
import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.repository.WemeetDataStore;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IWemeetViewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Controller
public class MyPageController {
    // 프로필, 계정 수정, 저장된 모임 결과 다시 보기를 담당하는 컨트롤러.

    private static final String PROFILE_EDIT_VERIFIED = "PROFILE_EDIT_VERIFIED";

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final WemeetViewHelper viewHelper;
    private final WemeetDataStore store;
    private final ObjectMapper objectMapper;

    public MyPageController(
            IWemeetViewService viewService,
            IApiAuthService authService,
            WemeetViewHelper viewHelper,
            WemeetDataStore store,
            ObjectMapper objectMapper
    ) {
        this.viewService = viewService;
        this.authService = authService;
        this.viewHelper = viewHelper;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        List<MeetingDTO.CreatedMeeting> createdMeetings = viewService.getCreatedMeetings(currentUser.getId());
        List<MeetingDTO.CreatedMeeting> participatingMeetings = viewService.getParticipatingMeetings(currentUser.getId());
        viewHelper.populateCommon(model, "profile", false);
        model.addAttribute("profile", viewHelper.toProfile(
                currentUser,
                createdMeetings.size(),
                viewService.getFriends(currentUser.getId()).size(),
                participatingMeetings.size()
        ));
        model.addAttribute("createdMeetings", createdMeetings);
        model.addAttribute("participatingMeetings", participatingMeetings);
        return "profile/index";
    }

    @GetMapping("/profile/meetings/results")
    public String createdMeetingResults(
            @RequestParam String meetingId,
            @RequestParam(defaultValue = "car") String routeMode,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        WemeetDataStore.MeetingRecord meeting = store.findMeetingForUser(currentUser.getId(), meetingId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Meeting not found"
                ));
        List<String> selectedFriendIds = meeting.participantIds().stream()
                .filter(participantId -> !participantId.equals(currentUser.getId()))
                .toList();

        viewHelper.populateCommon(model, "profile", false);
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
        model.addAttribute("meetingCreationAvailable", false);
        String normalizedRouteMode = viewHelper.normalizeRouteMode(routeMode);
        model.addAttribute("selectedRouteMode", normalizedRouteMode);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        RecommendationDTO.RecommendationBundle savedRecommendation = deserializeRecommendationSnapshot(meeting);
        if (savedRecommendation != null && meeting.recommendationSnapshotExpiresAt() != null
                && meeting.recommendationSnapshotExpiresAt().isAfter(now)) {
            model.addAttribute("recommendation", savedRecommendation);
            model.addAttribute("recommendationSnapshotNotice", "저장된 추천 결과를 보여주고 있습니다.");
            model.addAttribute("recommendationSnapshotExpiresOn", meeting.recommendationSnapshotExpiresAt().toLocalDate());
        } else {
            if (meeting.recommendationSnapshotExpiresAt() != null && meeting.recommendationSnapshotExpiresAt().isBefore(now)) {
                model.addAttribute("recommendationSnapshotNotice", "저장된 추천 결과 보관 기간이 지나 최신 기준으로 다시 계산했습니다.");
            }
            model.addAttribute("recommendation", viewService.buildRecommendation(
                    currentUser.getId(),
                    meeting.category(),
                    selectedFriendIds,
                    savedMode,
                    savedAnchorId,
                    normalizedRouteMode
            ));
        }
        return "meeting/results";
    }

    private RecommendationDTO.RecommendationBundle deserializeRecommendationSnapshot(WemeetDataStore.MeetingRecord meeting) {
        if (meeting.recommendationSnapshotJson() == null || meeting.recommendationSnapshotJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(meeting.recommendationSnapshotJson(), RecommendationDTO.RecommendationBundle.class);
        } catch (Exception exception) {
            return null;
        }
    }

    @PostMapping("/profile/meetings/delete")
    public String deleteCreatedMeeting(
            @RequestParam String meetingId,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        store.deleteMeetingCreatedByUser(currentUser.getId(), meetingId);
        redirectAttributes.addFlashAttribute("profileNotice", "모임이 삭제되었습니다.");
        return "redirect:/profile";
    }

    @GetMapping("/profile/verify-password")
    public String profilePasswordCheck(Model model, HttpSession session) {
        viewHelper.requireLoggedInUser(session);
        viewHelper.populateCommon(model, "profile", false);
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

        viewHelper.populateCommon(model, "profile", false);
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

        viewHelper.populateCommon(model, "profile", false);
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
