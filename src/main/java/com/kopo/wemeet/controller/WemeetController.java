package com.kopo.wemeet.controller;

import com.kopo.wemeet.config.KakaoMapProperties;
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
    // Thymeleaf 화면 렌더링을 담당하는 웹 컨트롤러다.
    // 세션 기반 로그인 처리와 페이지별 모델 구성을 여기서 묶는다.

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final KakaoMapProperties kakaoMapProperties;

    public WemeetController(IWemeetViewService viewService, IApiAuthService authService, KakaoMapProperties kakaoMapProperties) {
        this.viewService = viewService;
        this.authService = authService;
        this.kakaoMapProperties = kakaoMapProperties;
    }

    @GetMapping("/")
    public String landing(Model model, HttpSession session) {
        AppUser currentUser = findLoggedInUser(session);
        if (currentUser != null) {
            populateHomeModel(model, currentUser);
            return "home";
        }

        populateCommon(model, "guest-home", true);
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        return "landing";
    }

    @GetMapping("/login")
    public String login(@RequestParam(defaultValue = "false") boolean registered,
                        @RequestParam(defaultValue = "false") boolean error,
                        Model model) {
        populateCommon(model, "login", true);
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("registered", registered);
        model.addAttribute("error", error);
        return "login";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        populateCommon(model, "login", true);
        return "signup";
    }

    @GetMapping("/guest/plan")
    public String guestPlan(
            @RequestParam(defaultValue = "") String guestAddress,
            @RequestParam(defaultValue = "맛집") String category,
            Model model
    ) {
        populateCommon(model, "nearby", true);
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("guestAddress", guestAddress);
        model.addAttribute("selectedCategory", category);
        return "guest-plan";
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

        // 화면 이동 중에도 로그인 상태를 유지하려고 필요한 최소 정보만 세션에 저장한다.
        session.setAttribute("AUTH_TOKEN", authResponse.token());
        session.setAttribute("USER_ID", authResponse.user().id());
        session.setAttribute("USER_NICKNAME", authResponse.user().nickname());

        return "redirect:/";
    }

    @PostMapping("/guest/preview")
    public String guestPreview(
            @RequestParam(defaultValue = "") String guestAddress,
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(defaultValue = "CENTER") String mode,
            RedirectAttributes redirectAttributes
    ) {
        redirectAttributes.addAttribute("guest", true);
        redirectAttributes.addAttribute("guestAddress", guestAddress);
        redirectAttributes.addAttribute("category", category);
        redirectAttributes.addAttribute("mode", mode);
        return "redirect:/search/results";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/home")
    public String home(Model model, HttpSession session) {
        requireLoggedInUser(session);
        return "redirect:/";
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
            // 리다이렉트 뒤에도 결과 문구를 보여주기 위해 flash attribute를 사용한다.
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
        // 카테고리는 사용자가 직접 누르도록 기본 선택을 비워 둔다.
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
            @RequestParam(required = false) String guestAddress,
            Model model,
            HttpSession session

    ) {
        AppUser currentUser = null;
        if (!guest) {
            currentUser = requireLoggedInUser(session);
        }
        System.out.println("enabled: " + kakaoMapProperties.isEnabled());
        System.out.println("key: " + kakaoMapProperties.getJavascriptKey());
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
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = requireLoggedInUser(session);
        populateRecommendationModel(model, "create", currentUser, category, friendIds, mode, anchorId, false, null);
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
            boolean guestMode,
            String guestAddress
    ) {
        // 추천 결과 화면은 게스트/로그인 사용자 모두 같은 템플릿을 사용한다.
        populateCommon(model, activeTab, guestMode);
        UiModels.UserProfile guestProfile = guestMode ? viewService.getGuestUser(guestAddress) : null;
        model.addAttribute("profile", guestMode ? guestProfile : toProfile(currentUser));
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

    private void populateCommon(Model model, String activeTab, boolean guestMode) {
        // 여러 페이지에서 반복되는 공통 화면 속성은 한곳에서 채운다.
        model.addAttribute("appName", "모임 장소 찾기");
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("guestMode", guestMode);
        model.addAttribute("kakaoMapEnabled", kakaoMapProperties.isEnabled());
        model.addAttribute("kakaoMapJavascriptKey", kakaoMapProperties.getJavascriptKey());
    }


    private void populateHomeModel(Model model, AppUser currentUser) {
        populateCommon(model, "home", false);
        model.addAttribute("profile", toProfile(currentUser));
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("upcomingMeetings", viewService.getUpcomingMeetings());
    }

    private AppUser findLoggedInUser(HttpSession session) {
        if (session == null) {
            return null;
        }

        String token = (String) session.getAttribute("AUTH_TOKEN");
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            return authService.requireUser("Bearer " + token);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 401) {
                session.invalidate();
                return null;
            }
            throw exception;
        }
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
        AppUser user = findLoggedInUser(session);
        if (user == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Login required");
        }
        return user;
    }

    private UiModels.UserProfile toProfile(AppUser user) {
        // 현재 프로필 통계 값은 시연용 고정값이고, 나머지 기본 정보는 실제 사용자 데이터를 쓴다.
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
