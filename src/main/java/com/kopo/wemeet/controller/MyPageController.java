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
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;

import static com.kopo.wemeet.util.UiDefaults.DEFAULT_ROUTE_MODE;
import static com.kopo.wemeet.util.UiDefaults.TAB_PROFILE;

/**
 * MyPageController는 화면 요청과 API 요청을 받아 서비스 계층으로 넘기는 MVC 컨트롤러.
 */
@Controller
@RequiredArgsConstructor
public class MyPageController {
    // 프로필, 계정 수정, 저장된 모임 결과 다시 보기 담당 컨트롤러.
    // 컨트롤러는 사용자의 요청을 받고, 실제 조회/저장/삭제 작업은 서비스 클래스에 위임.

    // 비밀번호 확인 통과 여부를 세션에 표시할 때 사용하는 이름.
    // 프로필 수정이나 회원탈퇴처럼 민감한 화면은 이 값이 true일 때만 접근 허용.
    private static final String PROFILE_EDIT_VERIFIED = "PROFILE_EDIT_VERIFIED";

    // 화면에 보여줄 데이터 조회 담당 서비스.
    private final IWemeetViewService viewService;
    // 로그인 사용자 정보 수정, 비밀번호 확인, 회원탈퇴 같은 인증/계정 작업 담당.
    private final IApiAuthService authService;
    // 로그인 여부 확인, 공통 메뉴 정보 세팅, 화면용 프로필 변환 헬퍼.
    private final WemeetViewHelper viewHelper;
    // 모임 초대 응답, 모임 삭제, 저장된 모임 조회 같은 모임 작업 담당.
    private final IMeetingService meetingService;
    // DB에 JSON 문자열로 저장된 추천 결과를 다시 Java 객체로 변환할 때 사용.
    private final ObjectMapper objectMapper;

    // 마이페이지 첫 화면.
    // 내가 만든 모임, 참여 중인 모임, 초대 목록, 프로필 요약 정보를 모아서 profile/index.html로 전달.
    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        // 마이페이지는 로그인한 사용자만 볼 수 있으므로 세션에서 현재 사용자 확인.
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        // 내가 만든 모임 목록에 네이버 지도 링크 같은 화면용 정보 추가.
        List<MeetingDTO.CreatedMeeting> createdMeetings = enrichProfileMeetings(
                viewService.getCreatedMeetings(currentUser.getId()),
                currentUser.getId()
        );
        // 내가 초대받아 참여 중인 모임 목록도 같은 방식으로 화면용 정보 추가.
        List<MeetingDTO.CreatedMeeting> participatingMeetings = enrichProfileMeetings(
                viewService.getParticipatingMeetings(currentUser.getId()),
                currentUser.getId()
        );

        // 공통 상단 메뉴와 현재 탭 정보를 모델에 추가.
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

    // 마이페이지에서 저장된 모임의 추천 결과를 다시 보는 화면.
    // 저장된 결과가 남아 있으면 그대로 표시, 없으면 현재 데이터 기준으로 다시 계산.
    @GetMapping("/profile/meetings/results")
    public String createdMeetingResults(
            @RequestParam String meetingId,
            @RequestParam(defaultValue = DEFAULT_ROUTE_MODE) String routeMode,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        // 요청한 모임이 없거나, 현재 사용자가 볼 수 없는 모임이면 404 에러.
        MeetingDTO.MeetingRecord meeting = meetingService.findMeetingForUser(currentUser.getId(), meetingId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Meeting not found"
                ));

        // 추천 계산 화면에서 "선택된 친구"로 표시하기 위해 참가자 중 현재 사용자 제외.
        List<String> selectedFriendIds = meeting.participantIds().stream()
                .filter(participantId -> !participantId.equals(currentUser.getId()))
                .toList();

        viewHelper.populateCommon(model, TAB_PROFILE, false);
        model.addAttribute("profile", viewHelper.toProfile(currentUser));
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("guestAddress", "");
        model.addAttribute("selectedCategory", meeting.category());
        String savedMode = RecommendationMode.from(meeting.recommendationMode()).name();

        // 저장된 기준 참가자가 없으면 현재 사용자를 기본 기준으로 사용.
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

        // 모임 생성 당시 저장해 둔 추천 결과 JSON을 다시 객체로 복원.
        RecommendationDTO.RecommendationBundle savedRecommendation = deserializeRecommendationSnapshot(meeting);
        if (savedRecommendation != null) {
            // 저장된 모임 장소가 추천 목록 맨 위에 오도록 정렬.
            model.addAttribute("recommendation", prioritizeSavedMeetingPlace(savedRecommendation, meeting));
            model.addAttribute("recommendationSnapshotNotice", "저장된 추천 결과를 보여주고 있습니다.");
            if (meeting.recommendationSnapshotExpiresAt() != null) {
                model.addAttribute("recommendationSnapshotExpiresOn", meeting.recommendationSnapshotExpiresAt().toLocalDate());
            }
        } else {
            // 저장된 스냅샷이 없으면 장소 추천 새로 계산.
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

    // 받은 모임 초대 수락 또는 거절 처리.
    @PostMapping("/profile/meetings/invitations/respond")
    public String respondMeetingInvitation(
            @RequestParam(defaultValue = "") String meetingId,
            @RequestParam(defaultValue = "") String action,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        boolean accept = "accept".equalsIgnoreCase(action);

        // action 값이 accept이면 수락, 그 외 값이면 거절 처리.
        meetingService.respondMeetingInvitation(currentUser.getId(), meetingId, accept);
        redirectAttributes.addFlashAttribute(
                "profileNotice",
                accept ? "모임 초대를 수락했습니다." : "모임 초대를 거절했습니다."
        );
        return "redirect:/profile";
    }

    // DB에 문자열로 저장된 추천 결과 JSON을 RecommendationBundle 객체로 변환.
    // 저장값이 없거나 JSON 파싱에 실패하면 null 반환 후 새로 계산.
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

    // 저장된 추천 결과에서 실제 모임 장소를 첫 번째 장소로 이동.
    // 사용자는 "내가 저장한 장소"를 먼저 기대하므로 추천 목록 순서를 화면에 맞게 조정.
    private RecommendationDTO.RecommendationBundle prioritizeSavedMeetingPlace(
            RecommendationDTO.RecommendationBundle snapshot,
            MeetingDTO.MeetingRecord meeting
    ) {
        if (snapshot.venues() == null || snapshot.venues().isEmpty()) {
            return snapshot;
        }

        // 추천 장소 목록에서 저장된 모임 장소 위치 검색.
        int selectedVenueIndex = findSavedMeetingVenueIndex(snapshot.venues(), meeting);
        if (selectedVenueIndex <= 0) {
            return selectedVenueIndex == 0 ? copySnapshotWithSelectedVenueMapPoints(snapshot, snapshot.venues()) : snapshot;
        }

        // 저장된 장소가 2번째 이후에 있으면 목록에서 빼서 맨 앞으로 이동.
        List<RecommendationDTO.VenueOption> reorderedVenues = new ArrayList<>(snapshot.venues());
        RecommendationDTO.VenueOption selectedVenue = reorderedVenues.remove(selectedVenueIndex);
        reorderedVenues.add(0, selectedVenue);

        return copySnapshotWithSelectedVenueMapPoints(snapshot, reorderedVenues);
    }

    // 추천 장소 목록에서 DB에 저장된 모임 장소와 같은 항목 위치 검색.
    // 먼저 이름+주소가 모두 같은 장소 검색, 없으면 이름만 같은 장소 검색.
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

    // 추천 결과 객체를 새로 만들어 장소 목록과 지도 마커 순서 맞춤.
    // 첫 번째 장소가 지도에서도 선택된 장소로 표시되도록 mapPoints 재구성.
    private RecommendationDTO.RecommendationBundle copySnapshotWithSelectedVenueMapPoints(
            RecommendationDTO.RecommendationBundle snapshot,
            List<RecommendationDTO.VenueOption> venues
    ) {
        RecommendationDTO.VenueOption selectedVenue = venues.get(0);

        // 화면 상단 요약에 표시되는 평균 시간/공정성 값도 선택된 장소 기준으로 변경.
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
            // 기존 지도 마커 중 장소 마커는 새 순서로 다시 만들 것이므로 제외.
            snapshot.mapPoints().stream()
                    .filter(point -> !"venue".equals(point.markerType()))
                    .forEach(mapPoints::add);
        }
        // 정렬된 장소 목록을 바탕으로 장소 마커 재추가.
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

    // null이 아닌 두 문자열의 앞뒤 공백을 제거한 뒤 비교.
    private boolean sameText(String left, String right) {
        return left != null && right != null && left.trim().equals(right.trim());
    }

    // 문자열이 null이거나 빈 문자열이면 true 반환.
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 프로필에 표시할 모임 목록에 네이버 지도 링크 추가.
    private List<MeetingDTO.CreatedMeeting> enrichProfileMeetings(List<MeetingDTO.CreatedMeeting> meetings, String viewerUserId) {
        return meetings.stream()
                .map(meeting -> meetingService.findMeetingForUser(viewerUserId, meeting.id())
                        .map(record -> copyMeetingWithNaverMapUrl(meeting, buildMeetingNaverMapUrl(record, viewerUserId)))
                        .orElse(meeting))
                .toList();
    }

    // 기존 모임 카드 데이터는 유지하고, 네이버 지도 링크만 추가한 새 객체 생성.
    // DTO가 불변 record라서 값을 바꾸는 대신 새 record 생성 후 반환.
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

    // 모임 장소로 바로 길찾기할 수 있는 네이버 지도 URL 생성.
    private String buildMeetingNaverMapUrl(MeetingDTO.MeetingRecord meeting, String viewerUserId) {
        if (meeting.meetingPlaceName() == null || meeting.meetingPlaceName().isBlank()) {
            return "";
        }

        // 저장된 추천 결과에서 출발지와 목적지 좌표 검색.
        RecommendationDTO.RecommendationBundle snapshot = deserializeRecommendationSnapshot(meeting);
        RecommendationDTO.MapPoint originPoint = resolveMeetingOriginPoint(snapshot, viewerUserId);
        RecommendationDTO.VenueOption destinationVenue = resolveMeetingDestinationVenue(snapshot, meeting);
        if (originPoint == null || destinationVenue == null) {
            // 좌표를 못 찾으면 길찾기 대신 장소 검색 URL 생성.
            return "https://map.naver.com/p/search/" + org.springframework.web.util.UriUtils.encodePathSegment(
                    meeting.meetingPlaceName().trim(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        }

        // 출발지와 목적지 좌표가 모두 있으면 네이버 지도 길찾기 URL 생성.
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

    // 저장된 지도 마커 중 현재 사용자의 출발지 마커 검색.
    // 정확한 사용자 ID 마커가 없으면 참가자/기준점 마커 중 첫 번째 사용.
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

    // 저장된 추천 장소 중 실제 모임 장소와 가장 잘 맞는 목적지 검색.
    // 이름+주소가 맞는 장소 우선 검색, 없으면 이름만 맞는 장소, 그것도 없으면 첫 번째 장소 사용.
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

    // 여러 문자열 중 처음으로 비어 있지 않은 값 반환.
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    // 내가 만든 모임 삭제.
    // 서비스에서 "현재 사용자가 만든 모임인지" 확인한 뒤 삭제.
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

    // 프로필 수정 전 비밀번호 확인 화면.
    @GetMapping("/profile/verify-password")
    public String profilePasswordCheck(Model model, HttpSession session) {
        viewHelper.requireLoggedInUser(session);
        viewHelper.populateCommon(model, TAB_PROFILE, false);
        return "profile/password-check";
    }

    // 사용자가 입력한 현재 비밀번호 확인.
    // 성공하면 세션에 확인 완료 표시 후 프로필 수정 화면으로 이동.
    @PostMapping("/profile/verify-password")
    public String verifyProfilePassword(
            @RequestParam(defaultValue = "") String password,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        if (!authService.matchesPassword(currentUser, password)) {
            // 비밀번호가 틀리면 다시 확인 화면으로 이동, 에러 메시지는 한 번만 표시.
            redirectAttributes.addFlashAttribute("profileVerifyPasswordError", "비밀번호가 올바르지 않습니다.");
            return "redirect:/profile/verify-password";
        }

        session.setAttribute(PROFILE_EDIT_VERIFIED, true);
        return "redirect:/profile/edit";
    }

    // 프로필 수정 화면.
    // 비밀번호 확인을 통과한 사용자만 비밀번호/주소 수정 탭 접근 가능.
    @GetMapping("/profile/edit")
    public String profileEdit(
            @RequestParam(defaultValue = "password") String editTab,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        if (!Boolean.TRUE.equals(session.getAttribute(PROFILE_EDIT_VERIFIED))) {
            // 직접 URL로 접근한 경우 먼저 비밀번호 확인 화면으로 이동.
            return "redirect:/profile/verify-password";
        }

        viewHelper.populateCommon(model, TAB_PROFILE, false);
        model.addAttribute("profile", viewHelper.toProfile(currentUser));
        model.addAttribute("selectedProfileEditTab", "address".equalsIgnoreCase(editTab) ? "address" : "password");
        return "profile/edit";
    }

    // 회원탈퇴 확인 화면.
    // 계정 삭제도 민감한 작업이므로 프로필 수정과 같은 비밀번호 확인 절차 사용.
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

    @PostMapping("/profile/image")
    public String updateProfileImage(
            @RequestParam("profileImage") MultipartFile profileImage,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        authService.updateProfileImage(currentUser, profileImage);

        redirectAttributes.addFlashAttribute(
                "profileNotice",
                "프로필 이미지가 변경되었습니다."
        );

        return "redirect:/profile";
    }

    // 사용자의 기본 출발지 주소 수정.
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

    // 사용자의 비밀번호를 새 비밀번호로 변경.
    @PostMapping("/profile/password")
    public String updateProfilePassword(
            @RequestParam(defaultValue = "") String newPassword,
            @RequestParam(defaultValue = "") String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        // 새 비밀번호가 비어 있으면 저장하지 않고 수정 화면에 에러 표시.
        if (newPassword == null || newPassword.isBlank()) {
            redirectAttributes.addFlashAttribute("profilePasswordError", "새 비밀번호를 입력해주세요.");
            redirectAttributes.addAttribute("editTab", "password");
            return "redirect:/profile/edit";
        }

        // 새 비밀번호와 확인 입력값이 같아야 변경 허용.
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

    // 회원탈퇴 실제 처리.
    // 계정 삭제 후 세션을 무효화해서 즉시 로그아웃 상태로 변경.
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

