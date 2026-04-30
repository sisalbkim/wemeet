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
    // 로그인, 회원가입, 비밀번호 찾기 같은 인증 화면 흐름을 처리하는 컨트롤러.

    private static final String PASSWORD_RESET_USER_ID = "PASSWORD_RESET_USER_ID";
    private static final String PASSWORD_RESET_USER_ID_INPUT = "PASSWORD_RESET_USER_ID_INPUT";
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

    @GetMapping({"/login", "/user/login"})
    public String login(
            @RequestParam(defaultValue = "false") boolean registered,
            @RequestParam(defaultValue = "false") boolean error,
            Model model
    ) {
        viewHelper.populateCommon(model, "login", true);
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("registered", registered);
        model.addAttribute("error", error);
        return "user/login";
    }

    @GetMapping({"/signup", "/user/userRegForm"})
    public String signup(Model model, HttpSession session) {
        populateSignupModel(model, session);
        return "user/userRegForm";
    }

    @GetMapping({"/find-id", "/user/findId"})
    public String findId(Model model) {
        populateFindIdModel(model);
        return "user/find-id";
    }

    @GetMapping({"/find-password", "/user/findPassword"})
    public String findPassword(Model model) {
        populateFindPasswordModel(model);
        return "user/find-password";
    }

    @GetMapping({"/find-password/reset", "/user/findPassword/reset"})
    public String resetPasswordPage(Model model, HttpSession session) {
        if (session == null || session.getAttribute(PASSWORD_RESET_USER_ID) == null) {
            return "redirect:/user/findPassword";
        }
        viewHelper.populateCommon(model, "login", true);
        model.addAttribute("passwordResetUserId", session.getAttribute(PASSWORD_RESET_USER_ID_INPUT));
        model.addAttribute("passwordResetEmail", session.getAttribute(PASSWORD_RESET_EMAIL));
        return "user/reset-password";
    }

    @PostMapping({"/signup", "/user/insertUserInfo"})
    public String signupSubmit(
            @RequestParam(defaultValue = "") String nickname,
            @RequestParam(name = "userId", defaultValue = "") String userId,
            @RequestParam(name = "loginId", defaultValue = "") String legacyLoginId,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String confirmPassword,
            @RequestParam(defaultValue = "") String baseAddress,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedUserId = resolveUserIdInput(userId, legacyLoginId);
        String normalizedEmail = CmmUtil.nvl(email);

        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("signupError", "비밀번호 확인이 일치하지 않습니다.");
            redirectAttributes.addFlashAttribute("signupNickname", nickname);
            redirectAttributes.addFlashAttribute("signupUserId", normalizedUserId);
            redirectAttributes.addFlashAttribute("signupEmail", normalizedEmail);
            redirectAttributes.addFlashAttribute("signupBaseAddress", baseAddress);
            return "redirect:/user/userRegForm";
        }

        String verifiedEmail = (String) session.getAttribute("SIGNUP_VERIFIED_EMAIL");
        if (verifiedEmail == null || !verifiedEmail.equalsIgnoreCase(normalizedEmail)) {
            redirectAttributes.addFlashAttribute("signupError", "이메일 중복확인과 이메일 인증을 먼저 완료해주세요.");
            redirectAttributes.addFlashAttribute("signupNickname", nickname);
            redirectAttributes.addFlashAttribute("signupUserId", normalizedUserId);
            redirectAttributes.addFlashAttribute("signupEmail", normalizedEmail);
            redirectAttributes.addFlashAttribute("signupBaseAddress", baseAddress);
            return "redirect:/user/userRegForm";
        }

        try {
            authService.signUp(new AuthDTO.SignUpRequest(
                    nickname,
                    normalizedUserId,
                    password,
                    normalizedEmail,
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
            redirectAttributes.addFlashAttribute("signupUserId", normalizedUserId);
            redirectAttributes.addFlashAttribute("signupEmail", normalizedEmail);
            redirectAttributes.addFlashAttribute("signupBaseAddress", baseAddress);
            return "redirect:/user/userRegForm";
        }

        session.removeAttribute("SIGNUP_VERIFICATION_EMAIL");
        session.removeAttribute("SIGNUP_VERIFICATION_CODE");
        session.removeAttribute("SIGNUP_VERIFIED_EMAIL");

        redirectAttributes.addAttribute("registered", true);
        redirectAttributes.addFlashAttribute("registeredNickname", nickname.isBlank() ? normalizedUserId : nickname);
        return "redirect:/user/login";
    }

    @PostMapping({"/login", "/user/loginProc"})
    public String loginSubmit(
            @RequestParam(name = "userId", defaultValue = "") String userId,
            @RequestParam(name = "loginId", defaultValue = "") String legacyLoginId,
            @RequestParam(defaultValue = "") String password,
            HttpSession session
    ) {
        String normalizedUserId = resolveUserIdInput(userId, legacyLoginId);
        AuthDTO.AuthResponse loginResult = authService.login(new AuthDTO.LoginRequest(normalizedUserId, password));
        session.setAttribute("AUTH_TOKEN", loginResult.token());
        session.setAttribute("USER_ID", loginResult.user().id());
        session.setAttribute("USER_NICKNAME", loginResult.user().nickname());
        return "redirect:/";
    }

    @PostMapping({"/find-id", "/user/findIdProc"})
    public String findIdSubmit(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String email,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedName = CmmUtil.nvl(name);
        String normalizedEmail = CmmUtil.nvl(email);
        try {
            String foundUserId = authService.findLoginIdByNameAndEmail(normalizedName, normalizedEmail);
            redirectAttributes.addFlashAttribute("foundUserId", foundUserId);
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
        return "redirect:/user/findId";
    }

    @PostMapping({"/find-password/verify", "/user/findPassword/verify"})
    public String findPasswordVerify(
            @RequestParam(name = "userId", defaultValue = "") String userId,
            @RequestParam(name = "loginId", defaultValue = "") String legacyLoginId,
            @RequestParam(defaultValue = "") String email,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedUserId = resolveUserIdInput(userId, legacyLoginId);
        String normalizedEmail = CmmUtil.nvl(email);
        try {
            String accountId = authService.findUserIdByLoginIdAndEmail(normalizedUserId, normalizedEmail);
            session.setAttribute(PASSWORD_RESET_USER_ID, accountId);
            session.setAttribute(PASSWORD_RESET_USER_ID_INPUT, normalizedUserId);
            session.setAttribute(PASSWORD_RESET_EMAIL, normalizedEmail);
            redirectAttributes.addFlashAttribute("passwordResetUserId", normalizedUserId);
            redirectAttributes.addFlashAttribute("passwordResetEmail", normalizedEmail);
            redirectAttributes.addFlashAttribute("passwordResetLookupSuccess", "계정이 확인되었습니다. 새 비밀번호를 입력해주세요.");
            return "redirect:/user/findPassword/reset";
        } catch (ResponseStatusException exception) {
            session.removeAttribute(PASSWORD_RESET_USER_ID);
            session.removeAttribute(PASSWORD_RESET_USER_ID_INPUT);
            session.removeAttribute(PASSWORD_RESET_EMAIL);
            redirectAttributes.addFlashAttribute("passwordResetLookupError", switch (exception.getStatusCode().value()) {
                case 400 -> "아이디와 올바른 이메일을 입력해주세요.";
                case 404 -> "아이디와 이메일이 일치하는 계정을 찾지 못했습니다.";
                default -> "비밀번호 변경 대상 계정을 확인하지 못했습니다.";
            });
            redirectAttributes.addFlashAttribute("passwordResetUserId", normalizedUserId);
            redirectAttributes.addFlashAttribute("passwordResetEmail", normalizedEmail);
            return "redirect:/user/findPassword";
        }
    }

    @PostMapping({"/find-password/reset", "/user/findPassword/reset"})
    public String resetPassword(
            @RequestParam(defaultValue = "") String newPassword,
            @RequestParam(defaultValue = "") String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String accountId = session == null ? null : (String) session.getAttribute(PASSWORD_RESET_USER_ID);

        if (accountId == null || accountId.isBlank()) {
            return "redirect:/user/findPassword";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("passwordResetConfirmError", "비밀번호 확인이 일치하지 않습니다.");
            return "redirect:/user/findPassword/reset";
        }

        try {
            authService.resetPasswordForUser(accountId, newPassword);
            session.removeAttribute(PASSWORD_RESET_USER_ID);
            session.removeAttribute(PASSWORD_RESET_USER_ID_INPUT);
            session.removeAttribute(PASSWORD_RESET_EMAIL);
            redirectAttributes.addFlashAttribute("passwordResetSuccess", "비밀번호가 변경되었습니다.");
            return "redirect:/user/login";
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("passwordResetConfirmError", switch (exception.getStatusCode().value()) {
                case 400 -> "새 비밀번호를 입력해주세요.";
                case 404 -> "비밀번호를 변경할 계정을 찾지 못했습니다.";
                default -> "비밀번호를 변경하지 못했습니다.";
            });
            return "redirect:/user/findPassword/reset";
        }
    }

    @GetMapping({"/logout", "/user/logout"})
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
    @GetMapping("/user/getUserIdExists")
    public AuthDTO.LoginIdAvailabilityResponse userLoginIdAvailable(
            @RequestParam(name = "userId", defaultValue = "") String userId,
            @RequestParam(name = "loginId", defaultValue = "") String legacyLoginId
    ) {
        boolean available = authService.isLoginIdAvailable(resolveUserIdInput(userId, legacyLoginId));
        return new AuthDTO.LoginIdAvailabilityResponse(
                available,
                available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다."
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
        model.addAttribute("signupVerifiedUserId", session.getAttribute("SIGNUP_VERIFIED_USER_ID"));
    }

    private void populateFindPasswordModel(Model model) {
        viewHelper.populateCommon(model, "login", true);
    }

    private void populateFindIdModel(Model model) {
        viewHelper.populateCommon(model, "login", true);
    }

    private String resolveUserIdInput(String userId, String legacyLoginId) {
        String normalizedUserId = CmmUtil.nvl(userId);
        return normalizedUserId.isBlank() ? CmmUtil.nvl(legacyLoginId) : normalizedUserId;
    }
}
