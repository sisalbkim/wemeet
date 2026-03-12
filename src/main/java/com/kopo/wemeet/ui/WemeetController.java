package com.kopo.wemeet.ui;

import com.kopo.wemeet.api.ApiAuthService;
import com.kopo.wemeet.api.ApiDtos;
import com.kopo.wemeet.auth.AppUser;
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

    private final WemeetViewService viewService;
    private final ApiAuthService authService;

    public WemeetController(WemeetViewService viewService, ApiAuthService authService) {
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
        model.addAttribute("friends", viewService.getFriends());
        return "friends";
    }

    @GetMapping("/history")
    public String history(
            @RequestParam(defaultValue = "전체") String filter,
            @RequestParam(defaultValue = "") String keyword,
            Model model,
            HttpSession session
    ) {
        requireLoggedInUser(session);
        populateCommon(model, "history", false);
        model.addAttribute("selectedFilter", filter);
        model.addAttribute("keyword", keyword);
        model.addAttribute("categories", viewService.getCategories());
        model.addAttribute("searchHistory", viewService.getSearchHistory(filter, keyword));
        return "history";
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        AppUser currentUser = requireLoggedInUser(session);
        populateCommon(model, "profile", false);
        model.addAttribute("profile", toProfile(currentUser));
        return "profile";
    }

    @GetMapping("/meetings/new")
    public String meetingForm(Model model, HttpSession session) {
        AppUser currentUser = requireLoggedInUser(session);
        populateCommon(model, "create", false);
        model.addAttribute("profile", toProfile(currentUser));
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("friends", viewService.getFriends());
        model.addAttribute("selectedCategory", "맛집");
        return "meeting-form";
    }

    @GetMapping("/search/results")
    public String quickResults(
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(required = false) List<String> friendIds,
            @RequestParam(defaultValue = "false") boolean guest,
            Model model,
            HttpSession session
    ) {
        if (!guest) {
            requireLoggedInUser(session);
        }
        populateRecommendationModel(model, guest ? "nearby" : "home", category, friendIds, guest);
        return "search-results";
    }

    @PostMapping("/meetings/preview")
    public String previewResults(
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(required = false) List<String> friendIds,
            @RequestParam(defaultValue = "") String meetingName,
            @RequestParam(defaultValue = "") String meetingDate,
            Model model,
            HttpSession session
    ) {
        requireLoggedInUser(session);
        populateRecommendationModel(model, "create", category, friendIds, false);
        model.addAttribute("meetingName", meetingName);
        model.addAttribute("meetingDate", meetingDate);
        return "search-results";
    }

    private void populateRecommendationModel(Model model, String activeTab, String category, List<String> friendIds, boolean guestMode) {
        populateCommon(model, activeTab, guestMode);
        model.addAttribute("profile", guestMode ? viewService.getCurrentUser() : viewService.getCurrentUser());
        model.addAttribute("categories", viewService.getCategories().stream().filter(chip -> !"전체".equals(chip.label())).toList());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedFriendIds", friendIds == null ? List.of() : friendIds);
        model.addAttribute("recommendation", viewService.buildRecommendation(category, friendIds));
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
