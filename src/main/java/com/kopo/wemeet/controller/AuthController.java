package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.*;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IWemeetViewService;
import com.kopo.wemeet.util.CmmUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private static final String PASSWORD_RESET_USER_ID = "PASSWORD_RESET_USER_ID";
    private static final String PASSWORD_RESET_LOGIN_ID = "PASSWORD_RESET_LOGIN_ID";
    private static final String PASSWORD_RESET_EMAIL = "PASSWORD_RESET_EMAIL";

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final WemeetViewHelper viewHelper;

    public AuthController(
            IWemeetViewService viewService,
            IApiAuthService authService,
            WemeetViewHelper viewHelper
    ) {
        this.viewService = viewService;
        this.authService = authService;
        this.viewHelper = viewHelper;
    }

    @GetMapping("/login")
    public String login(
            @RequestParam(defaultValue = "false") boolean registered,
            @RequestParam(defaultValue = "false") boolean error,
            Model model
    ) {
        viewHelper.populateCommon(model, "login", true);
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("registered", registered);
        model.addAttribute("error", error);
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signup(Model model, HttpSession session) {
        populateSignupModel(model, session);
        return "auth/signup";
    }

    @GetMapping("/find-id")
    public String findId(Model model) {
        populateFindIdModel(model);
        return "auth/find-id";
    }

    @GetMapping("/find-password")
    public String findPassword(Model model) {
        populateFindPasswordModel(model);
        return "auth/find-password";
    }

    @GetMapping("/find-password/reset")
    public String resetPasswordPage(Model model, HttpSession session) {
        if (session == null || session.getAttribute(PASSWORD_RESET_USER_ID) == null) {
            return "redirect:/find-password";
        }
        viewHelper.populateCommon(model, "login", true);
        model.addAttribute("passwordResetLoginId", session.getAttribute(PASSWORD_RESET_LOGIN_ID));
        model.addAttribute("passwordResetEmail", session.getAttribute(PASSWORD_RESET_EMAIL));
        return "auth/reset-password";
    }

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
        String trimmedEmail = CmmUtil.nvl(email);

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
            authService.signUp(new AuthDTO.SignUpRequest(
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
            HttpSession session
    ) {
        AuthDTO.AuthResponse authResponse = authService.login(new AuthDTO.LoginRequest(loginId, password));
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
        String normalizedName = CmmUtil.nvl(name);
        String normalizedEmail = CmmUtil.nvl(email);
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
            @RequestParam(defaultValue = "") String loginId,
            @RequestParam(defaultValue = "") String email,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedLoginId = CmmUtil.nvl(loginId);
        String normalizedEmail = CmmUtil.nvl(email);
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
            session.removeAttribute(PASSWORD_RESET_USER_ID);
            session.removeAttribute(PASSWORD_RESET_LOGIN_ID);
            session.removeAttribute(PASSWORD_RESET_EMAIL);
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

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @ResponseBody
    @PostMapping("/api/auth/signup")
    public AuthDTO.AuthResponse apiSignUp(@RequestBody AuthDTO.SignUpRequest request) {
        return authService.signUp(request);
    }

    @ResponseBody
    @PostMapping("/api/auth/login")
    public AuthDTO.AuthResponse apiLogin(@RequestBody AuthDTO.LoginRequest request) {
        return authService.login(request);
    }

    @ResponseBody
    @PostMapping("/api/auth/password/reset-request")
    public AuthDTO.PasswordResetResponse apiCreatePasswordResetToken(@RequestBody AuthDTO.PasswordResetRequest request) {
        return authService.createPasswordResetToken(request);
    }

    @ResponseBody
    @PostMapping("/api/auth/password/reset-confirm")
    public AuthDTO.PasswordResetResponse apiResetPassword(@RequestBody AuthDTO.PasswordResetConfirmRequest request) {
        return authService.resetPassword(request);
    }

    @ResponseBody
    @GetMapping("/api/auth/email/available")
    public AuthDTO.EmailAvailabilityResponse apiEmailAvailable(@RequestParam String email) {
        boolean available = authService.isEmailAvailable(email);
        return new AuthDTO.EmailAvailabilityResponse(
                available,
                available ? "사용 가능한 이메일입니다." : "이미 사용 중인 이메일입니다."
        );
    }

    @ResponseBody
    @PostMapping("/api/auth/email/send-code")
    public AuthDTO.EmailVerificationSendResponse apiSendEmailVerificationCode(
            @RequestBody AuthDTO.EmailVerificationSendRequest request,
            HttpSession session
    ) {
        String normalizedEmail = CmmUtil.nvl(request.email());
        String code = authService.createSignupEmailVerificationCode(normalizedEmail);
        session.setAttribute("SIGNUP_VERIFICATION_EMAIL", normalizedEmail);
        session.setAttribute("SIGNUP_VERIFICATION_CODE", code);
        session.removeAttribute("SIGNUP_VERIFIED_EMAIL");
        return new AuthDTO.EmailVerificationSendResponse(
                true,
                "인증코드를 전송했습니다. 메일 연동 전 단계라 화면에서 preview 코드를 같이 보여줍니다.",
                code
        );
    }

    @ResponseBody
    @PostMapping("/api/auth/email/verify-code")
    public AuthDTO.EmailVerificationConfirmResponse apiVerifyEmailVerificationCode(
            @RequestBody AuthDTO.EmailVerificationConfirmRequest request,
            HttpSession session
    ) {
        String normalizedEmail = CmmUtil.nvl(request.email());
        String pendingEmail = (String) session.getAttribute("SIGNUP_VERIFICATION_EMAIL");
        String pendingCode = (String) session.getAttribute("SIGNUP_VERIFICATION_CODE");
        String inputCode = CmmUtil.nvl(request.code());

        if (normalizedEmail.isBlank() || inputCode.isBlank()) {
            return new AuthDTO.EmailVerificationConfirmResponse(false, "이메일과 인증코드를 입력해주세요.");
        }
        if (!normalizedEmail.equalsIgnoreCase(pendingEmail) || pendingCode == null) {
            return new AuthDTO.EmailVerificationConfirmResponse(false, "먼저 해당 이메일로 인증코드를 전송해주세요.");
        }
        if (!pendingCode.equals(inputCode)) {
            return new AuthDTO.EmailVerificationConfirmResponse(false, "인증코드가 일치하지 않습니다.");
        }

        session.setAttribute("SIGNUP_VERIFIED_EMAIL", normalizedEmail);
        return new AuthDTO.EmailVerificationConfirmResponse(true, "이메일 인증이 완료되었습니다.");
    }

    @ResponseBody
    @GetMapping("/api/me")
    public UserDTO.UserResponse apiMe(@RequestHeader("Authorization") String authorization) {
        return authService.toUserResponse(authService.requireUser(authorization));
    }

    @ResponseBody
    @PostMapping("/api/me/address")
    public UserDTO.UserResponse apiUpdateAddress(
            @RequestHeader("Authorization") String authorization,
            @RequestBody UserDTO.AddressUpdateRequest request
    ) {
        AppUser requester = authService.requireUser(authorization);
        return authService.updateBaseAddress(requester, request.baseAddress());
    }

    private void populateSignupModel(Model model, HttpSession session) {
        viewHelper.populateCommon(model, "login", true);
        model.addAttribute("signupVerifiedEmail", session.getAttribute("SIGNUP_VERIFIED_EMAIL"));
    }

    private void populateFindPasswordModel(Model model) {
        viewHelper.populateCommon(model, "login", true);
    }

    private void populateFindIdModel(Model model) {
        viewHelper.populateCommon(model, "login", true);
    }
}
