package com.kopo.wemeet.controller;

import com.kopo.wemeet.config.NaverMapProperties;
import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.dto.UiModels;
import com.kopo.wemeet.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IWemeetViewService;
import com.kopo.wemeet.service.impl.NaverPlaceSearchService;
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
import java.util.Optional;

@Controller
public class WemeetController {
    // Thymeleaf 화면 렌더링을 담당하는 웹 컨트롤러다.
    // 세션 기반 로그인 처리와 페이지별 모델 구성을 여기서 묶는다.
    private static final String PASSWORD_RESET_USER_ID = "PASSWORD_RESET_USER_ID";
    private static final String PASSWORD_RESET_LOGIN_ID = "PASSWORD_RESET_LOGIN_ID";
    private static final String PASSWORD_RESET_EMAIL = "PASSWORD_RESET_EMAIL";
    private static final String PROFILE_EDIT_VERIFIED = "PROFILE_EDIT_VERIFIED";

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final NaverMapProperties naverMapProperties;
    private final NaverPlaceSearchService naverPlaceSearchService;

    public WemeetController(
            IWemeetViewService viewService,
            IApiAuthService authService,
            NaverMapProperties naverMapProperties,
            NaverPlaceSearchService naverPlaceSearchService
    ) {
        this.viewService = viewService;
        this.authService = authService;
        this.naverMapProperties = naverMapProperties;
        this.naverPlaceSearchService = naverPlaceSearchService;
    }

    // 비로그인 진입 페이지와 로그인/회원가입 관련 화면 라우트다.
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

    @GetMapping("/login1")
    public String loginMock(@RequestParam(defaultValue = "false") boolean registered,
                            @RequestParam(defaultValue = "false") boolean error,
                            Model model) {
        populateCommon(model, "login", true);
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("registered", registered);
        model.addAttribute("error", error);
        return "login1";
    }

    @GetMapping("/login2")
    public String loginMockImage(Model model) {
        populateCommon(model, "login", true);
        return "login2";
    }

    @GetMapping("/email0")
    public String emailMockPage(Model model) {
        populateCommon(model, "login", true);
        return "email0";
    }

    @GetMapping("/email1")
    public String emailMockTextPage(Model model) {
        populateCommon(model, "login", true);
        return "email1";
    }

    @GetMapping("/signup")
    public String signup(Model model, HttpSession session) {
        populateSignupModel(model, session, null);
        return "signup";
    }

    @GetMapping("/signup1")
    public String signupMock1(Model model, HttpSession session) {
        populateSignupModel(model, session, 1);
        return "signup";
    }

    @GetMapping("/signup2")
    public String signupMock2(Model model, HttpSession session) {
        populateSignupModel(model, session, 2);
        return "signup";
    }

    @GetMapping("/signup3")
    public String signupMock3(Model model, HttpSession session) {
        populateSignupModel(model, session, 3);
        return "signup";
    }

    @GetMapping("/signup4")
    public String signupMock4(Model model, HttpSession session) {
        populateSignupModel(model, session, 4);
        return "signup";
    }

    @GetMapping("/signup5")
    public String signupMock5(Model model, HttpSession session) {
        populateSignupModel(model, session, 5);
        return "signup";
    }

    @GetMapping("/signup6")
    public String signupMock6(Model model, HttpSession session) {
        populateSignupModel(model, session, 6);
        return "signup";
    }

    @GetMapping("/signup7")
    public String signupMock7(Model model, HttpSession session) {
        populateSignupModel(model, session, 7);
        return "signup";
    }

    @GetMapping("/signup8")
    public String signupMock8(Model model, HttpSession session) {
        populateSignupModel(model, session, null);
        model.addAttribute("signupMockVerifyFeedback", "인증코드를 전송했습니다");
        model.addAttribute("signupMockVerifyFeedbackTone", "is-success");
        model.addAttribute("hideShellNavigation", true);
        return "signup";
    }

    private void populateSignupModel(Model model, HttpSession session, Integer redPlaceholderIndex) {
        populateCommon(model, "login", true);
        model.addAttribute("signupVerifiedEmail", session.getAttribute("SIGNUP_VERIFIED_EMAIL"));
        model.addAttribute("redPlaceholderIndex", redPlaceholderIndex);
        model.addAttribute("hideShellNavigation", redPlaceholderIndex != null);
    }

    // 계정 찾기와 게스트 체험 진입 화면을 준비하는 라우트다.
    @GetMapping("/find-id")
    public String findId(Model model) {
        populateFindIdModel(model, null);
        return "find-id";
    }

    @GetMapping("/find-id1")
    public String findIdMock1(Model model) {
        populateFindIdModel(model, 1);
        return "find-id";
    }

    @GetMapping("/find-id2")
    public String findIdMock2(Model model) {
        populateFindIdModel(model, 2);
        return "find-id";
    }

    @GetMapping("/find-password")
    public String findPassword(Model model) {
        populateFindPasswordModel(model, null);
        return "find-password";
    }

    @GetMapping("/find-password0")
    public String findPasswordMock0(Model model) {
        populateFindPasswordModel(model, 0);
        model.addAttribute("passwordResetName", "홍길동");
        model.addAttribute("passwordResetEmail", "hong@example.com");
        model.addAttribute("passwordResetLoginId", "wemeet_user");
        model.addAttribute("passwordResetTemporaryPassword", "WM123456!");
        model.addAttribute("passwordResetLookupSuccess", "일치하는 계정을 확인했습니다. 아래 임시 비밀번호로 로그인해 주세요.");
        return "find-password";
    }

    @GetMapping("/find-password1")
    public String findPasswordMock1(Model model) {
        populateFindPasswordModel(model, 1);
        return "find-password";
    }

    @GetMapping("/find-password2")
    public String findPasswordMock2(Model model) {
        populateFindPasswordModel(model, 2);
        return "find-password";
    }

    @GetMapping("/find-password3")
    public String findPasswordMock3(Model model) {
        populateFindPasswordModel(model, 3);
        return "find-password";
    }

    @GetMapping("/find-password/reset")
    public String resetPasswordPage(Model model, HttpSession session) {
        if (session == null || session.getAttribute(PASSWORD_RESET_USER_ID) == null) {
            return "redirect:/find-password";
        }
        populateCommon(model, "login", true);
        model.addAttribute("passwordResetLoginId", session.getAttribute(PASSWORD_RESET_LOGIN_ID));
        model.addAttribute("passwordResetEmail", session.getAttribute(PASSWORD_RESET_EMAIL));
        return "reset-password";
    }

    @GetMapping("/guest/plan")
    public String guestPlan(
            @RequestParam(defaultValue = "") String guestAddress,
            @RequestParam(defaultValue = "맛집") String category,
            Model model
    ) {
        populateGuestPlanModel(model, guestAddress, category, null);
        return "guest-plan";
    }

    @GetMapping("/guest/plan1")
    public String guestPlanMock1(
            @RequestParam(defaultValue = "") String guestAddress,
            @RequestParam(defaultValue = "맛집") String category,
            Model model
    ) {
        populateGuestPlanModel(model, guestAddress, category, 1);
        return "guest-plan";
    }

    @GetMapping("/guest/places")
    public String guestPlaces(
            @RequestParam(defaultValue = "") String originQuery,
            @RequestParam(defaultValue = "") String guestAddress,
            @RequestParam(defaultValue = "") String tag,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "5") Integer display,
            Model model
    ) {
        String effectiveOriginQuery = originQuery == null || originQuery.isBlank() ? guestAddress : originQuery;
        String effectiveTag = tag == null || tag.isBlank() ? category : tag;
        populateGuestPlaceSearchModel(model, effectiveOriginQuery, effectiveTag, display);
        return "place-search";
    }

    // 폼 제출을 처리해 세션/DB를 갱신하고 다시 적절한 화면으로 보내는 POST 라우트들이다.
    @PostMapping("/signup")
    public String signupSubmit(
            @RequestParam(defaultValue = "") String nickname,
            @RequestParam(defaultValue = "") String loginId,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String confirmPassword,
            @RequestParam(defaultValue = "") String baseAddress,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String trimmedEmail = email == null ? "" : email.trim();

        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("signupError", "비밀번호 확인이 일치하지 않습니다.");
            redirectAttributes.addFlashAttribute("signupNickname", nickname);
            redirectAttributes.addFlashAttribute("signupLoginId", loginId);
            redirectAttributes.addFlashAttribute("signupEmail", trimmedEmail);
            redirectAttributes.addFlashAttribute("signupBaseAddress", baseAddress);
            return "redirect:/signup";
        }

        String verifiedEmail = (String) session.getAttribute("SIGNUP_VERIFIED_EMAIL");
        if (verifiedEmail == null || !verifiedEmail.equalsIgnoreCase(trimmedEmail)) {
            redirectAttributes.addFlashAttribute("signupError", "이메일 중복확인과 이메일 인증을 먼저 완료해주세요.");
            redirectAttributes.addFlashAttribute("signupNickname", nickname);
            redirectAttributes.addFlashAttribute("signupLoginId", loginId);
            redirectAttributes.addFlashAttribute("signupEmail", trimmedEmail);
            redirectAttributes.addFlashAttribute("signupBaseAddress", baseAddress);
            return "redirect:/signup";
        }

        try {
            authService.signUp(new ApiDtos.SignUpRequest(
                    nickname,
                    loginId,
                    password,
                    trimmedEmail,
                    baseAddress
            ));
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("signupError", switch (exception.getReason()) {
                case "email already exists" -> "이미 사용 중인 이메일입니다.";
                case "loginId already exists" -> "이미 사용 중인 아이디입니다.";
                case "nickname, loginId, password, and email are required" -> "필수 입력값을 모두 작성해주세요.";
                default -> exception.getReason();
            });
            redirectAttributes.addFlashAttribute("signupNickname", nickname);
            redirectAttributes.addFlashAttribute("signupLoginId", loginId);
            redirectAttributes.addFlashAttribute("signupEmail", trimmedEmail);
            redirectAttributes.addFlashAttribute("signupBaseAddress", baseAddress);
            return "redirect:/signup";
        }

        session.removeAttribute("SIGNUP_VERIFICATION_EMAIL");
        session.removeAttribute("SIGNUP_VERIFICATION_CODE");
        session.removeAttribute("SIGNUP_VERIFIED_EMAIL");

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

    @PostMapping("/find-id")
    public String findIdSubmit(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String email,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedEmail = email == null ? "" : email.trim();
        try {
            String loginId = authService.findLoginIdByNameAndEmail(normalizedName, normalizedEmail);
            redirectAttributes.addFlashAttribute("foundLoginId", loginId);
            redirectAttributes.addFlashAttribute("foundName", normalizedName);
            redirectAttributes.addFlashAttribute("foundEmail", normalizedEmail);
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("findIdError", switch (exception.getStatusCode().value()) {
                case 400 -> "이름과 올바른 이메일 형식을 입력해주세요.";
                case 404 -> "이름과 이메일이 일치하는 계정을 찾지 못했습니다.";
                default -> "아이디를 조회하지 못했습니다.";
            });
            redirectAttributes.addFlashAttribute("foundName", normalizedName);
            redirectAttributes.addFlashAttribute("foundEmail", normalizedEmail);
        }
        return "redirect:/find-id";
    }

    @PostMapping("/find-password/verify")
    public String findPasswordVerify(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String loginId,
            @RequestParam(defaultValue = "") String email,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedLoginId = loginId == null ? "" : loginId.trim();
        String normalizedEmail = email == null ? "" : email.trim();
        try {
            String resetUserId = authService.findUserIdByLoginIdAndEmail(normalizedLoginId, normalizedEmail);
            session.setAttribute(PASSWORD_RESET_USER_ID, resetUserId);
            session.setAttribute(PASSWORD_RESET_LOGIN_ID, normalizedLoginId);
            session.setAttribute(PASSWORD_RESET_EMAIL, normalizedEmail);
            redirectAttributes.addFlashAttribute("passwordResetLoginId", normalizedLoginId);
            redirectAttributes.addFlashAttribute("passwordResetEmail", normalizedEmail);
            redirectAttributes.addFlashAttribute("passwordResetLookupSuccess", "계정이 확인되었습니다. 새 비밀번호를 입력해주세요.");
            return "redirect:/find-password/reset";
        } catch (ResponseStatusException exception) {
            if (session != null) {
                session.removeAttribute(PASSWORD_RESET_USER_ID);
                session.removeAttribute(PASSWORD_RESET_LOGIN_ID);
                session.removeAttribute(PASSWORD_RESET_EMAIL);
            }
            redirectAttributes.addFlashAttribute("passwordResetLookupError", switch (exception.getStatusCode().value()) {
                case 400 -> "아이디와 올바른 이메일을 입력해주세요.";
                case 404 -> "아이디와 이메일이 일치하는 계정을 찾지 못했습니다.";
                default -> "비밀번호 변경 대상 계정을 확인하지 못했습니다.";
            });
            redirectAttributes.addFlashAttribute("passwordResetLoginId", normalizedLoginId);
            redirectAttributes.addFlashAttribute("passwordResetEmail", normalizedEmail);
            return "redirect:/find-password";
        }
    }

    @PostMapping("/find-password/reset")
    public String resetPassword(
            @RequestParam(defaultValue = "") String newPassword,
            @RequestParam(defaultValue = "") String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String resetUserId = session == null ? null : (String) session.getAttribute(PASSWORD_RESET_USER_ID);

        if (resetUserId == null || resetUserId.isBlank()) {
            return "redirect:/find-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("passwordResetConfirmError", "비밀번호 확인이 일치하지 않습니다.");
            return "redirect:/find-password/reset";
        }

        try {
            authService.resetPasswordForUser(resetUserId, newPassword);
            session.removeAttribute(PASSWORD_RESET_USER_ID);
            session.removeAttribute(PASSWORD_RESET_LOGIN_ID);
            session.removeAttribute(PASSWORD_RESET_EMAIL);
            redirectAttributes.addFlashAttribute("passwordResetSuccess", "비밀번호가 변경되었습니다.");
            return "redirect:/login";
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("passwordResetConfirmError", switch (exception.getStatusCode().value()) {
                case 400 -> "새 비밀번호를 입력해주세요.";
                case 404 -> "비밀번호를 변경할 계정을 찾지 못했습니다.";
                default -> "비밀번호를 변경하지 못했습니다.";
            });
            return "redirect:/find-password/reset";
        }
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

    // 로그인 이후 사용하는 메인 기능 화면 라우트다.
    @GetMapping("/home")
    public String home(Model model, HttpSession session) {
        requireLoggedInUser(session);
        return "redirect:/";
    }

    @GetMapping("/friends")
    public String friends(
            @RequestParam(defaultValue = "") String keyword,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = requireLoggedInUser(session);
        populateFriendsModel(model, toProfile(currentUser), viewService.getFriends(currentUser.getId()), keyword, null, false);
        return "friends";
    }

    @GetMapping("/friends4")
    public String friendsMock4(Model model) {
        UiModels.UserProfile profile = viewService.getGuestUser();
        List<UiModels.FriendSummary> sampleFriends = List.of(
                new UiModels.FriendSummary("friend-101", "이영희", "@user456", "성수동 출발", "2026. 3. 1.", true),
                new UiModels.FriendSummary("friend-102", "박민수", "@user789", "잠실동 출발", "2026. 3. 3.", false)
        );
        populateFriendsModel(model, profile, sampleFriends, "", null, true);
        return "friends";
    }

    @GetMapping("/friends1")
    public String friendsMock1(Model model) {
        UiModels.UserProfile profile = viewService.getGuestUser();
        populateFriendsModel(model, profile, List.of(), "", 1, true);
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
            redirectAttributes.addFlashAttribute("friendError", switch (exception.getReason()) {
                case "friendCode is required" -> "친구 코드를 입력해주세요.";
                case "Friend code not found" -> "일치하는 친구 코드를 찾지 못했습니다.";
                case "You cannot add yourself" -> "내 친구 코드는 직접 추가할 수 없습니다.";
                case "Friend already added" -> "이미 추가된 친구입니다.";
                default -> exception.getReason();
            });
        }
        return "redirect:" + redirectTo;
    }

    @PostMapping("/friends/favorite")
    public String updateFriendFavorite(
            @RequestParam String friendId,
            @RequestParam(defaultValue = "false") boolean favorite,
            @RequestParam(defaultValue = "") String keyword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = requireLoggedInUser(session);
        viewService.updateFriendFavorite(currentUser.getId(), friendId, favorite);
        if (keyword != null && !keyword.isBlank()) {
            redirectAttributes.addAttribute("keyword", keyword.trim());
        }
        return "redirect:/friends";
    }

    @PostMapping("/friends/request/respond")
    public String respondFriendRequest(
            @RequestParam(defaultValue = "") String action,
            @RequestParam(defaultValue = "/friends") String redirectTo,
            RedirectAttributes redirectAttributes
    ) {
        boolean approved = "approve".equalsIgnoreCase(action);
        redirectAttributes.addFlashAttribute("friendRequests", List.of());
        redirectAttributes.addFlashAttribute(
                "friendRequestNotice",
                approved ? "승인되었습니다!" : "거절했습니다."
        );
        redirectAttributes.addFlashAttribute("friendRequestNoticeTone", approved ? "success" : "error");
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

    @GetMapping("/history1")
    public String historyMock1(Model model) {
        populateCommon(model, "history", false);
        model.addAttribute("selectedFilter", "전체");
        model.addAttribute("keyword", "");
        model.addAttribute("categories", viewService.getCategories());
        model.addAttribute("searchHistory", List.of(
                new UiModels.SearchHistoryItem(1L, "강남 맛집", "맛집", "2026. 3. 10."),
                new UiModels.SearchHistoryItem(2L, "성수 카페", "카페", "2026. 3. 9."),
                new UiModels.SearchHistoryItem(3L, "잠실 놀거리", "놀이", "2026. 3. 8.")
        ));
        model.addAttribute("redPlaceholderIndex", 1);
        model.addAttribute("hideShellNavigation", true);
        return "history";
    }

    @PostMapping("/history/clear")
    public String clearHistory(
            @RequestParam(defaultValue = "전체") String filter,
            @RequestParam(defaultValue = "") String keyword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = requireLoggedInUser(session);
        viewService.clearSearchHistory(currentUser.getId());
        redirectAttributes.addAttribute("filter", filter);
        redirectAttributes.addAttribute("keyword", keyword);
        return "redirect:/history";
    }

    @PostMapping("/history/remove")
    public String removeHistory(
            @RequestParam Long historyId,
            @RequestParam(defaultValue = "전체") String filter,
            @RequestParam(defaultValue = "") String keyword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = requireLoggedInUser(session);
        viewService.removeSearchHistory(currentUser.getId(), historyId);
        redirectAttributes.addAttribute("filter", filter);
        redirectAttributes.addAttribute("keyword", keyword);
        return "redirect:/history";
    }

    // 프로필 확인, 비밀번호 검증, 주소/비밀번호 수정 흐름을 담당하는 라우트다.
    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        AppUser currentUser = requireLoggedInUser(session);
        populateCommon(model, "profile", false);
        model.addAttribute("profile", toProfile(currentUser));
        return "profile";
    }

    @GetMapping("/profile/verify-password")
    public String profilePasswordCheck(Model model, HttpSession session) {
        requireLoggedInUser(session);
        populateCommon(model, "profile", false);
        return "profile-password-check";
    }

    @GetMapping("/profile/verify-password1")
    public String profilePasswordCheckMock1(Model model) {
        populateCommon(model, "profile", false);
        model.addAttribute("redPlaceholderIndex", 1);
        model.addAttribute("hideShellNavigation", true);
        return "profile-password-check";
    }

    @PostMapping("/profile/verify-password")
    public String verifyProfilePassword(
            @RequestParam(defaultValue = "") String password,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = requireLoggedInUser(session);
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
        AppUser currentUser = requireLoggedInUser(session);
        if (!Boolean.TRUE.equals(session.getAttribute(PROFILE_EDIT_VERIFIED))) {
            return "redirect:/profile/verify-password";
        }

        populateCommon(model, "profile", false);
        model.addAttribute("profile", toProfile(currentUser));
        model.addAttribute("selectedProfileEditTab", "address".equalsIgnoreCase(editTab) ? "address" : "password");
        return "profile-edit";
    }

    @GetMapping("/profile/edit1")
    public String profileEditMock1(Model model) {
        populateCommon(model, "profile", false);
        model.addAttribute("profile", viewService.getGuestUser());
        model.addAttribute("selectedProfileEditTab", "address");
        model.addAttribute("redPlaceholderIndex", 1);
        model.addAttribute("hideShellNavigation", true);
        return "profile-edit";
    }

    @GetMapping("/ex")
    public String profileEditExample(Model model) {
        populateCommon(model, "profile", false);
        model.addAttribute("profile", viewService.getGuestUser());
        model.addAttribute("selectedProfileEditTab", "address");
        model.addAttribute("redPlaceholderIndex", 1);
        model.addAttribute("hideShellNavigation", true);
        return "profile-edit";
    }

    @GetMapping("/ex1")
    public String profileEditPasswordExample1(Model model) {
        populateCommon(model, "profile", false);
        model.addAttribute("profile", viewService.getGuestUser());
        model.addAttribute("selectedProfileEditTab", "password");
        model.addAttribute("redPlaceholderIndex", 1);
        model.addAttribute("hideShellNavigation", true);
        return "profile-edit";
    }

    @GetMapping("/ex2")
    public String profileEditPasswordExample2(Model model) {
        populateCommon(model, "profile", false);
        model.addAttribute("profile", viewService.getGuestUser());
        model.addAttribute("selectedProfileEditTab", "password");
        model.addAttribute("redPlaceholderIndex", 2);
        model.addAttribute("hideShellNavigation", true);
        return "profile-edit";
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

    @PostMapping("/profile/password")
    public String updateProfilePassword(
            @RequestParam(defaultValue = "") String newPassword,
            @RequestParam(defaultValue = "") String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = requireLoggedInUser(session);

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

    // 모임 생성과 추천 결과 미리보기 화면을 만드는 라우트다.
    @GetMapping("/meetings/new")
    public String meetingForm(Model model, HttpSession session) {
        AppUser currentUser = requireLoggedInUser(session);
        List<UiModels.FriendSummary> friends = viewService.getFriends(currentUser.getId());
        populateMeetingFormModel(model, toProfile(currentUser), friends, extractFriendIds(friends), null, false);
        return "meeting-form";
    }

    @GetMapping("/meetings/new1")
    public String meetingFormMock1(Model model) {
        UiModels.UserProfile profile = viewService.getGuestUser();
        List<UiModels.FriendSummary> friends = viewService.getFriends(profile.id());
        populateMeetingFormModel(model, profile, friends, extractFriendIds(friends), 1, true);
        return "meeting-form";
    }

    @GetMapping("/meetings/new2")
    public String meetingFormMock2(Model model) {
        UiModels.UserProfile profile = viewService.getGuestUser();
        List<UiModels.FriendSummary> friends = viewService.getFriends(profile.id());
        populateMeetingFormModel(model, profile, friends, extractFriendIds(friends), 2, true);
        return "meeting-form";
    }

    @GetMapping("/meetings/new3")
    public String meetingFormMock3(Model model) {
        UiModels.UserProfile profile = viewService.getGuestUser();
        List<UiModels.FriendSummary> friends = viewService.getFriends(profile.id());
        populateMeetingFormModel(model, profile, friends, extractFriendIds(friends), 3, true);
        return "meeting-form";
    }

    @GetMapping("/meetings/new4")
    public String meetingFormMock4(Model model) {
        UiModels.UserProfile profile = viewService.getGuestUser();
        List<UiModels.FriendSummary> sampleFriends = List.of(
                new UiModels.FriendSummary("friend-201", "이영희", "@user456", "성수동 출발", "2026. 3. 1.", false),
                new UiModels.FriendSummary("friend-202", "박민수", "@user789", "잠실동 출발", "2026. 3. 3.", false)
        );
        populateMeetingFormModel(model, profile, sampleFriends, List.of(), null, true);
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

    // 아래 private 메서드들은 템플릿별로 필요한 Model 속성을 채워 넣는 조립 계층이다.
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

    private void populateGuestPlanModel(Model model, String guestAddress, String category, Integer redPlaceholderIndex) {
        populateCommon(model, "nearby", true);
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("guestAddress", guestAddress);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("redPlaceholderIndex", redPlaceholderIndex);
        model.addAttribute("hideShellNavigation", redPlaceholderIndex != null);
    }

    private void populateGuestPlaceSearchModel(
            Model model,
            String originQuery,
            String tag,
            Integer display
    ) {
        populateCommon(model, "nearby", true);
        model.addAttribute("originQuery", originQuery);
        model.addAttribute("tag", tag);
        model.addAttribute("display", display == null ? 5 : display);
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());

        if (originQuery == null || originQuery.isBlank() || tag == null || tag.isBlank()) {
            return;
        }

        try {
            ApiDtos.PlaceSearchResponse placeSearch = naverPlaceSearchService.search(new ApiDtos.PlaceSearchRequest(originQuery, tag, display));
            model.addAttribute("placeSearch", placeSearch);
            model.addAttribute("placeMapPoints", buildPlaceMapPoints(placeSearch));
        } catch (ResponseStatusException exception) {
            model.addAttribute("placeSearchError", Optional.ofNullable(exception.getReason()).orElse("장소 검색 중 오류가 발생했습니다."));
        }
    }

    private List<ApiDtos.MapPointResponse> buildPlaceMapPoints(ApiDtos.PlaceSearchResponse placeSearch) {
        List<ApiDtos.MapPointResponse> mapPoints = new java.util.ArrayList<>();
        mapPoints.add(new ApiDtos.MapPointResponse(
                "origin",
                placeSearch.origin().name(),
                placeSearch.origin().address(),
                placeSearch.origin().latitude(),
                placeSearch.origin().longitude(),
                "anchor",
                false
        ));

        for (int index = 0; index < placeSearch.places().size(); index++) {
            ApiDtos.PlaceCandidateResponse place = placeSearch.places().get(index);
            String address = place.roadAddress() == null || place.roadAddress().isBlank() ? place.address() : place.roadAddress();
            mapPoints.add(new ApiDtos.MapPointResponse(
                    "place-" + index,
                    place.name(),
                    address,
                    place.latitude(),
                    place.longitude(),
                    "venue",
                    index == 0
            ));
        }

        return mapPoints;
    }

    private void populateFindPasswordModel(Model model, Integer redPlaceholderIndex) {
        populateCommon(model, "login", true);
        model.addAttribute("redPlaceholderIndex", redPlaceholderIndex);
        model.addAttribute("hideShellNavigation", redPlaceholderIndex != null);
    }

    private void populateFindIdModel(Model model, Integer redPlaceholderIndex) {
        populateCommon(model, "login", true);
        model.addAttribute("redPlaceholderIndex", redPlaceholderIndex);
        model.addAttribute("hideShellNavigation", redPlaceholderIndex != null);
    }

    private void populateMeetingFormModel(
            Model model,
            UiModels.UserProfile profile,
            List<UiModels.FriendSummary> friends,
            List<String> preselectedFriendIds,
            Integer redPlaceholderIndex,
            boolean hideShellNavigation
    ) {
        populateCommon(model, "create", false);
        model.addAttribute("profile", profile);
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("friends", friends);
        model.addAttribute("preselectedFriendIds", preselectedFriendIds);
        model.addAttribute("selectedCategory", "");
        model.addAttribute("selectedMode", RecommendationMode.CENTER.name());
        model.addAttribute("selectedAnchorId", profile.id());
        model.addAttribute("redPlaceholderIndex", redPlaceholderIndex);
        model.addAttribute("hideShellNavigation", hideShellNavigation);
    }

    private List<String> extractFriendIds(List<UiModels.FriendSummary> friends) {
        return friends.stream().map(UiModels.FriendSummary::id).toList();
    }

    private void populateFriendsModel(
            Model model,
            UiModels.UserProfile profile,
            List<UiModels.FriendSummary> friends,
            String keyword,
            Integer redPlaceholderIndex,
            boolean hideShellNavigation
    ) {
        populateCommon(model, "friends", false);
        model.addAttribute("profile", profile);
        if (!model.containsAttribute("friendRequests")) {
            model.addAttribute("friendRequests", viewService.getFriendRequests());
        }
        model.addAttribute("friends", friends);
        model.addAttribute("friendKeyword", keyword == null ? "" : keyword);
        model.addAttribute("redPlaceholderIndex", redPlaceholderIndex);
        model.addAttribute("hideShellNavigation", hideShellNavigation);
    }

    private void populateCommon(Model model, String activeTab, boolean guestMode) {
        // 여러 페이지에서 반복되는 공통 화면 속성은 한곳에서 채운다.
        model.addAttribute("appName", "모임 장소 찾기");
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("guestMode", guestMode);
        model.addAttribute("naverMapEnabled", naverMapProperties.isEnabled());
        model.addAttribute("naverMapKeyId", naverMapProperties.getKeyId());
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
