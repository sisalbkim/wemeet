package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.*;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IWemeetViewService;
import com.kopo.wemeet.service.impl.MailDeliveryService;
import com.kopo.wemeet.service.impl.RememberMeJwtService;
import com.kopo.wemeet.util.CmmUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AuthController {
    // 로그인, 회원가입, 비밀번호 찾기 같은 인증 화면 흐름을 처리하는 컨트롤러.

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final WemeetViewHelper viewHelper;
    private final MailDeliveryService mailDeliveryService;
    private final RememberMeJwtService rememberMeJwtService;

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
        return "auth/login";
    }

    @GetMapping({"/signup", "/user/userRegForm"})
    public String signup(Model model, HttpSession session) {
        populateSignupModel(model, session);
        return "auth/signup";
    }

    @GetMapping({"/find-id", "/user/findId"})
    public String findId(Model model) {
        populateFindIdModel(model);
        return "auth/find-id";
    }

    @GetMapping({"/find-password", "/user/findPassword"})
    public String findPassword(Model model) {
        populateFindPasswordModel(model);
        return "auth/find-password";
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
            return "redirect:/signup";
        }

        String verifiedEmail = (String) session.getAttribute("SIGNUP_VERIFIED_EMAIL");
        if (verifiedEmail == null || !verifiedEmail.equalsIgnoreCase(normalizedEmail)) {
            redirectAttributes.addFlashAttribute("signupError", "이메일 중복확인과 이메일 인증을 먼저 완료해주세요.");
            redirectAttributes.addFlashAttribute("signupNickname", nickname);
            redirectAttributes.addFlashAttribute("signupUserId", normalizedUserId);
            redirectAttributes.addFlashAttribute("signupEmail", normalizedEmail);
            redirectAttributes.addFlashAttribute("signupBaseAddress", baseAddress);
            return "redirect:/signup";
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
            return "redirect:/signup";
        }

        session.removeAttribute("SIGNUP_VERIFICATION_EMAIL");
        session.removeAttribute("SIGNUP_VERIFICATION_CODE");
        session.removeAttribute("SIGNUP_VERIFIED_EMAIL");

        redirectAttributes.addAttribute("registered", true);
        redirectAttributes.addFlashAttribute("registeredNickname", nickname.isBlank() ? normalizedUserId : nickname);
        return "redirect:/login";
    }

    @PostMapping({"/login", "/user/loginProc"})
    public String loginSubmit(
            @RequestParam(name = "userId", defaultValue = "") String userId,
            @RequestParam(name = "loginId", defaultValue = "") String legacyLoginId,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "false") boolean rememberMe,
            HttpSession session,
            HttpServletResponse response
    ) {
        String normalizedUserId = resolveUserIdInput(userId, legacyLoginId);
        AuthDTO.AuthResponse loginResult = authService.login(new AuthDTO.LoginRequest(normalizedUserId, password));
        session.setAttribute("AUTH_TOKEN", loginResult.token());
        session.setAttribute("USER_ID", loginResult.user().id());
        session.setAttribute("USER_NICKNAME", loginResult.user().nickname());
        if (rememberMe) {
            rememberMeJwtService.writeRememberMeCookie(response, loginResult.user().id());
        } else {
            rememberMeJwtService.clearRememberMeCookie(response);
        }
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
            return "redirect:/find-id";
    }

    @PostMapping({"/find-password/verify", "/user/findPassword/verify"})
    public String findPasswordVerify(
            @RequestParam(name = "userId", defaultValue = "") String userId,
            @RequestParam(name = "loginId", defaultValue = "") String legacyLoginId,
            @RequestParam(defaultValue = "") String email,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedUserId = resolveUserIdInput(userId, legacyLoginId);
        String normalizedEmail = CmmUtil.nvl(email);
        try {
            AuthDTO.PasswordResetResponse response = authService.issueTemporaryPassword(normalizedUserId, normalizedEmail);
            redirectAttributes.addFlashAttribute("passwordResetSuccess", buildTemporaryPasswordNotice(response));
            if (response.resetTokenPreview() != null && !response.resetTokenPreview().isBlank()) {
                redirectAttributes.addFlashAttribute("passwordResetPreview", response.resetTokenPreview());
            }
            return "redirect:/login";
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("passwordResetLookupError", switch (exception.getStatusCode().value()) {
                case 400 -> "아이디와 올바른 이메일을 입력해주세요.";
                case 404 -> "아이디와 이메일이 일치하는 계정을 찾지 못했습니다.";
                case 503 -> "임시 비밀번호 메일 전송에 실패했습니다. 잠시 후 다시 시도해주세요.";
                default -> "임시 비밀번호를 발급하지 못했습니다.";
            });
            redirectAttributes.addFlashAttribute("passwordResetUserId", normalizedUserId);
            redirectAttributes.addFlashAttribute("passwordResetEmail", normalizedEmail);
            return "redirect:/find-password";
        }
    }

    @GetMapping({"/logout", "/user/logout"})
    public String logout(HttpSession session, HttpServletResponse response) {
        rememberMeJwtService.clearRememberMeCookie(response);
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
        MailDeliveryService.MailSendResult mailSendResult = mailDeliveryService.sendSignupVerificationCode(normalizedEmail, code);
        if (mailSendResult.sent()) {
            session.setAttribute("SIGNUP_VERIFICATION_EMAIL", normalizedEmail);
            session.setAttribute("SIGNUP_VERIFICATION_CODE", code);
            session.removeAttribute("SIGNUP_VERIFIED_EMAIL");
        } else {
            session.removeAttribute("SIGNUP_VERIFICATION_EMAIL");
            session.removeAttribute("SIGNUP_VERIFICATION_CODE");
            session.removeAttribute("SIGNUP_VERIFIED_EMAIL");
        }
        return new AuthDTO.EmailVerificationSendResponse(
                mailSendResult.sent(),
                mailSendResult.message(),
                mailSendResult.previewCode()
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

    private String resolveUserIdInput(String userId, String legacyLoginId) {
        String normalizedUserId = CmmUtil.nvl(userId);
        return normalizedUserId.isBlank() ? CmmUtil.nvl(legacyLoginId) : normalizedUserId;
    }

    private String buildTemporaryPasswordNotice(AuthDTO.PasswordResetResponse response) {
        if (response.resetTokenPreview() == null || response.resetTokenPreview().isBlank()) {
            return "임시 비밀번호를 이메일로 전송했습니다. 로그인 후 바로 비밀번호를 변경해 주세요.";
        }
        return "임시 비밀번호를 이메일로 전송했습니다. 메일 설정이 없어 임시 비밀번호를 함께 표시합니다: "
                + response.resetTokenPreview();
    }
}
