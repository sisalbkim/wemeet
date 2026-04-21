package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

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

@Controller
public class MyPageController {

    private static final String PROFILE_EDIT_VERIFIED = "PROFILE_EDIT_VERIFIED";

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final WemeetViewHelper viewHelper;

    public MyPageController(
            IWemeetViewService viewService,
            IApiAuthService authService,
            WemeetViewHelper viewHelper
    ) {
        this.viewService = viewService;
        this.authService = authService;
        this.viewHelper = viewHelper;
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        viewHelper.populateCommon(model, "profile", false);
        model.addAttribute("profile", viewHelper.toProfile(currentUser));
        return "profile";
    }

    @GetMapping("/profile/verify-password")
    public String profilePasswordCheck(Model model, HttpSession session) {
        viewHelper.requireLoggedInUser(session);
        viewHelper.populateCommon(model, "profile", false);
        return "profile-password-check";
    }

    @GetMapping("/profile/verify-password1")
    public String profilePasswordCheckMock1(Model model) {
        viewHelper.populateCommon(model, "profile", false);
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
        return "profile-edit";
    }

    @GetMapping("/profile/edit1")
    public String profileEditMock1(Model model) {
        viewHelper.populateCommon(model, "profile", false);
        model.addAttribute("profile", viewService.getGuestUser());
        model.addAttribute("selectedProfileEditTab", "address");
        model.addAttribute("redPlaceholderIndex", 1);
        model.addAttribute("hideShellNavigation", true);
        return "profile-edit";
    }

    @GetMapping("/ex")
    public String profileEditExample(Model model) {
        viewHelper.populateCommon(model, "profile", false);
        model.addAttribute("profile", viewService.getGuestUser());
        model.addAttribute("selectedProfileEditTab", "address");
        model.addAttribute("redPlaceholderIndex", 1);
        model.addAttribute("hideShellNavigation", true);
        return "profile-edit";
    }

    @GetMapping("/ex1")
    public String profileEditPasswordExample1(Model model) {
        viewHelper.populateCommon(model, "profile", false);
        model.addAttribute("profile", viewService.getGuestUser());
        model.addAttribute("selectedProfileEditTab", "password");
        model.addAttribute("redPlaceholderIndex", 1);
        model.addAttribute("hideShellNavigation", true);
        return "profile-edit";
    }

    @GetMapping("/ex2")
    public String profileEditPasswordExample2(Model model) {
        viewHelper.populateCommon(model, "profile", false);
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
}
