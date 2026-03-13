package com.kopo.wemeet.controller;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.dto.UiModels;
import com.kopo.wemeet.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IWemeetViewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
public class WemeetController {

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;

    public WemeetController(IWemeetViewService viewService, IApiAuthService authService) {
        this.viewService = viewService;
        this.authService = authService;
    }

    @GetMapping("/")
    public String landing(Model model) {
        populateCommon(model, "guest-home", true);
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        return "landing";
    }

    @GetMapping("/login")
    public String login(@RequestParam(defaultValue = "false") boolean registered,
                        @RequestParam(defaultValue = "false") boolean error,
                        Model model) {
        populateCommon(model, "login", true);
        model.addAttribute("registered", registered);
        model.addAttribute("error", error);
        return "login";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        populateCommon(model, "login", true);
        return "signup";
    }

    @PostMapping("/signup")
    public String signupSubmit(
            @RequestParam(defaultValue = "") String nickname,
            @RequestParam(defaultValue = "") String loginId,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String confirmPassword,
            @RequestParam(defaultValue = "") String baseAddress,
            RedirectAttributes redirectAttributes
    ) {
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("signupError", "비밀번호 확인이 일치하지 않습니다.");
            redirectAttributes.addFlashAttribute("signupNickname", nickname);
            redirectAttributes.addFlashAttribute("signupLoginId", loginId);
            redirectAttributes.addFlashAttribute("signupEmail", email);
            redirectAttributes.addFlashAttribute("signupBaseAddress", baseAddress);
            return "redirect:/signup";
        }

        authService.signUp(new ApiDtos.SignUpRequest(
                nickname,
                loginId,
                password,
                email,
                baseAddress
        ));

        redirectAttributes.addAttribute("registered", true);
        redirectAttributes.addFlashAttribute("registeredNickname", nickname.isBlank() ? loginId : nickname);
        return "redirect:/login";
    }

    @PostMapping("/login")
    public String loginSubmit(
            @RequestParam(defaultValue = "") String loginId,
            @RequestParam(defaultValue = "") String password,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        ApiDtos.AuthResponse authResponse = authService.login(new ApiDtos.LoginRequest(loginId, password));

        session.setAttribute("AUTH_TOKEN", authResponse.token());
        session.setAttribute("USER_ID", authResponse.user().id());
        session.setAttribute("USER_NICKNAME", authResponse.user().nickname());

        return "redirect:/home";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/home")
    public String home(Model model, HttpSession session) {
        AppUser currentUser = requireLoggedInUser(session);
        populateCommon(model, "home", false);
        model.addAttribute("profile", toProfile(currentUser));
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("upcomingMeetings", viewService.getUpcomingMeetings());
        return "home";
    }

    @GetMapping("/friends")
    public String friends(Model model, HttpSession session) {
        AppUser currentUser = requireLoggedInUser(session);
        populateCommon(model, "friends", false);
        model.addAttribute("profile", toProfile(currentUser));
        model.addAttribute("friendRequests", viewService.getFriendRequests());
        model.addAttribute("friends", viewService.getFriends(currentUser.getId()));
        return "friends";
    }

    @PostMapping("/friends/add")
    public String addFriend(
            @RequestParam(defaultValue = "") String friendCode,
            @RequestParam(defaultValue = "/friends") String redirectTo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = requireLoggedInUser(session);
        try {
            ApiDtos.UserResponse addedFriend = viewService.addFriendByCode(currentUser.getId(), friendCode);
            redirectAttributes.addFlashAttribute("friendNotice", addedFriend.nickname() + " 님을 친구로 추가했습니다.");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("friendError", exception.getReason());
        }
        return "redirect:" + redirectTo;
    }

    @GetMapping("/history")
    public String history(
            @RequestParam(defaultValue = "전체") String filter,
            @RequestParam(defaultValue = "") String keyword,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = requireLoggedInUser(session);
        populateCommon(model, "history", false);
        model.addAttribute("selectedFilter", filter);
        model.addAttribute("keyword", keyword);
        model.addAttribute("categories", viewService.getCategories());
        model.addAttribute("searchHistory", viewService.getSearchHistory(currentUser.getId(), filter, keyword));
        return "history";
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        AppUser currentUser = requireLoggedInUser(session);
        populateCommon(model, "profile", false);
        model.addAttribute("profile", toProfile(currentUser));
        return "profile";
    }

    @PostMapping("/profile/address")
    public String updateProfileAddress(
            @RequestParam(defaultValue = "") String baseAddress,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = requireLoggedInUser(session);
        authService.updateBaseAddress(currentUser, baseAddress);
        redirectAttributes.addFlashAttribute("profileNotice", "기본 출발지 주소가 수정되었습니다.");
        return "redirect:/profile";
    }

    @GetMapping("/meetings/new")
    public String meetingForm(Model model, HttpSession session) {
        AppUser currentUser = requireLoggedInUser(session);
        populateCommon(model, "create", false);
        model.addAttribute("profile", toProfile(currentUser));
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("friends", viewService.getFriends(currentUser.getId()));
        model.addAttribute("selectedCategory", "");
        model.addAttribute("selectedMode", RecommendationMode.CENTER.name());
        model.addAttribute("selectedAnchorId", currentUser.getId());
        return "meeting-form";
    }

    @GetMapping("/search/results")
    public String quickResults(
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(required = false) List<String> friendIds,
            @RequestParam(defaultValue = "CENTER") String mode,
            @RequestParam(required = false) String anchorId,
            @RequestParam(defaultValue = "false") boolean guest,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = null;
        if (!guest) {
            currentUser = requireLoggedInUser(session);
        }
        populateRecommendationModel(model, guest ? "nearby" : "home", currentUser, category, friendIds, mode, anchorId, guest);
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
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = requireLoggedInUser(session);
        populateRecommendationModel(model, "create", currentUser, category, friendIds, mode, anchorId, false);
        model.addAttribute("meetingName", meetingName);
        model.addAttribute("meetingDate", meetingDate);
        return "search-results";
    }

    private void populateRecommendationModel(
            Model model,
            String activeTab,
            AppUser currentUser,
            String category,
            List<String> friendIds,
            String mode,
            String anchorId,
            boolean guestMode
    ) {
        // 추천 결과 화면은 게스트/로그인 사용자 모두 같은 템플릿을 사용한다.
        populateCommon(model, activeTab, guestMode);
        model.addAttribute("profile", guestMode ? viewService.getGuestUser() : toProfile(currentUser));
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedMode", RecommendationMode.from(mode).name());
        model.addAttribute("selectedAnchorId", anchorId == null || anchorId.isBlank()
                ? (guestMode ? viewService.getGuestUser().id() : currentUser.getId())
                : anchorId);
        model.addAttribute("selectedFriendIds", friendIds == null ? List.of() : friendIds);
        model.addAttribute("recommendation", viewService.buildRecommendation(
                guestMode ? viewService.getGuestUser().id() : currentUser.getId(),
                category,
                friendIds,
                mode,
                anchorId
        ));
    }

    private void populateCommon(Model model, String activeTab, boolean guestMode) {
        model.addAttribute("appName", "모임 장소 찾기");
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("guestMode", guestMode);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String handleResponseStatusException(ResponseStatusException exception, RedirectAttributes redirectAttributes) {
        if (exception.getStatusCode().value() == 401) {
            if ("Login required".equals(exception.getReason())) {
                return "redirect:/login";
            }
            redirectAttributes.addFlashAttribute("loginErrorMessage", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return "redirect:/login?error=true";
        }

        if (exception.getStatusCode().value() == 409 || exception.getStatusCode().value() == 400) {
            redirectAttributes.addFlashAttribute("signupError", exception.getReason());
            return "redirect:/signup";
        }

        throw exception;
    }

    private AppUser requireLoggedInUser(HttpSession session) {
        String token = (String) session.getAttribute("AUTH_TOKEN");
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Login required");
        }
        // 세션에는 토큰만 저장하고 실제 사용자 조회는 서비스에 위임한다.
        return authService.requireUser("Bearer " + token);
    }

    private UiModels.UserProfile toProfile(AppUser user) {
        return new UiModels.UserProfile(
                user.getId(),
                user.getNickname(),
                "@" + user.getLoginId(),
                user.getFriendCode(),
                user.getBaseAddress(),
                "••••••••",
                2,
                2,
                3
        );
    }
}
