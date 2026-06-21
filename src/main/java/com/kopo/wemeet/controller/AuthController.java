package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.*;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.impl.MailDeliveryService;
import com.kopo.wemeet.util.CmmUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.function.Function;

/**
 * AuthController는 화면 요청과 API 요청을 받아 서비스 계층으로 위임하는 MVC 컨트롤러입니다.
 */
@Controller
@RequiredArgsConstructor
public class AuthController {
    // 로그인, 회원가입, 비밀번호 찾기 같은 인증 화면 흐름을 처리하는 컨트롤러.

    private final IApiAuthService authService;
    private final WemeetViewHelper viewHelper;
    private final MailDeliveryService mailDeliveryService;

    @GetMapping("/login")
    public String login(
            @RequestParam(defaultValue = "false") boolean error,
            Model model
    ) {
        viewHelper.populateCommon(model, "login", true);
        model.addAttribute("error", error);
        return "auth/login";
    }

    @PostMapping("/login")
    public String loginSubmit(
            @RequestParam(name = "userId", defaultValue = "") String userId,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "false") boolean autoLogin,
            HttpServletResponse response //서버가 브라우저에게 응답 보내기
    ) {
        String normalizedUserId = CmmUtil.nvl(userId);

        /*
         * 로그인 성공 후 세션에 AUTH_TOKEN/USER_ID를 저장하지 않는다.
         * Access/Refresh Token을 HttpOnly 쿠키로 내려주고, 이후 요청은 Access Token 쿠키로 인증한다.
         *
         * autoLogin=true  -> 브라우저를 닫아도 유지되는 persistent cookie
         * autoLogin=false -> 브라우저 세션 동안만 유지되는 session cookie
         */
        loginAndWriteCookies(new AuthDTO.LoginRequest(normalizedUserId, password), response, autoLogin);
        return "redirect:/";
    }

    @GetMapping("/signup")
    public String signup(Model model, HttpSession session) {
        populateSignupModel(model, session);
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signupSubmit(
            @RequestParam(defaultValue = "") String nickname,
            @RequestParam(name = "userId", defaultValue = "") String userId,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String confirmPassword,
            @RequestParam(defaultValue = "") String baseAddress,
            HttpSession session,
            RedirectAttributes redirectAttributes //redirect할 때 데이터를 같이 전달하기 위한 객체
    ) {
        String normalizedUserId = CmmUtil.nvl(userId);
        String normalizedEmail = CmmUtil.nvl(email);

        Function<String, String> redirectSignupError = message -> {
            redirectAttributes.addFlashAttribute("signupError", message);
            redirectAttributes.addFlashAttribute("signupNickname", nickname);
            redirectAttributes.addFlashAttribute("signupUserId", normalizedUserId);
            redirectAttributes.addFlashAttribute("signupEmail", normalizedEmail);
            redirectAttributes.addFlashAttribute("signupBaseAddress", baseAddress);
            return "redirect:/signup";
        };

        if (!password.equals(confirmPassword)) {
            return redirectSignupError.apply("비밀번호 확인이 일치하지 않습니다.");
        }

        String verifiedEmail = (String) session.getAttribute("SIGNUP_VERIFIED_EMAIL");
        if (verifiedEmail == null || !verifiedEmail.equalsIgnoreCase(normalizedEmail)) {
            return redirectSignupError.apply("이메일 중복확인과 이메일 인증을 먼저 완료해주세요.");
        }

        try {
            authService.signUp(createSignUpRequest(nickname, normalizedUserId, password, normalizedEmail, baseAddress));
        } catch (ResponseStatusException exception) {
            return redirectSignupError.apply(toSignupErrorMessage(exception));
        }

        clearSignupVerification(session);

        redirectAttributes.addFlashAttribute("signupSuccess", true);
        redirectAttributes.addFlashAttribute("registeredNickname", nickname.isBlank() ? normalizedUserId : nickname);
        return "redirect:/login";
    }

    @ResponseBody
    @GetMapping("/api/auth/user-id/available")
    public AuthDTO.LoginIdAvailabilityResponse userLoginIdAvailable(
            @RequestParam(name = "userId", defaultValue = "") String userId
    ) {
        boolean available = authService.isLoginIdAvailable(CmmUtil.nvl(userId));
        return new AuthDTO.LoginIdAvailabilityResponse(
                available,
                available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다."
        );
    }

    @GetMapping("/find-id")
    public String findId(Model model) {
        populateFindIdModel(model);
        return "auth/find-id";
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

    @GetMapping("/find-password")
    public String findPassword(Model model) {
        populateFindPasswordModel(model);
        return "auth/find-password";
    }

    @PostMapping("/find-password/verify")
    public String findPasswordVerify(
            @RequestParam(name = "userId", defaultValue = "") String userId,
            @RequestParam(defaultValue = "") String email,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedUserId = CmmUtil.nvl(userId);
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

    @GetMapping("/logout")
    public String logout(
            @CookieValue(name = "${app.auth.jwt.refresh-cookie-name:WM_REFRESH_TOKEN}", required = false) String refreshToken,
            HttpSession session,
            HttpServletResponse response
    ) {
        authService.revokeRefreshToken(refreshToken);
        authService.clearTokenCookies(response);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/";
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
        MailDeliveryService.MailSendResult mailSendResult = mailDeliveryService.sendSignupVerificationCode(normalizedEmail, code);
        if (mailSendResult.sent()) {
            session.setAttribute("SIGNUP_VERIFICATION_EMAIL", normalizedEmail);
            session.setAttribute("SIGNUP_VERIFICATION_CODE", code);
            session.removeAttribute("SIGNUP_VERIFIED_EMAIL");
        } else {
            clearSignupVerification(session);
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

    private AuthDTO.AuthResponse loginAndWriteCookies(
            AuthDTO.LoginRequest request,
            HttpServletResponse response,
            boolean persistent
    ) {
        AuthDTO.AuthResponse authResponse = authService.login(request);
        authService.writeTokenCookies(response, authResponse, persistent);
        return authResponse;
    }

    private AuthDTO.SignUpRequest createSignUpRequest(
            String nickname,
            String loginId,
            String password,
            String email,
            String baseAddress
    ) {
        return new AuthDTO.SignUpRequest(nickname, loginId, password, email, baseAddress);
    }

    private void clearSignupVerification(HttpSession session) {
        session.removeAttribute("SIGNUP_VERIFICATION_EMAIL");
        session.removeAttribute("SIGNUP_VERIFICATION_CODE");
        session.removeAttribute("SIGNUP_VERIFIED_EMAIL");
    }

    private String toSignupErrorMessage(ResponseStatusException exception) {
        return switch (exception.getReason()) {
            case "email already exists" -> "이미 사용 중인 이메일입니다.";
            case "loginId already exists" -> "이미 사용 중인 아이디입니다.";
            case "nickname, loginId, password, and email are required" -> "필수 입력값을 모두 작성해주세요.";
            default -> exception.getReason();
        };
    }

    private String buildTemporaryPasswordNotice(AuthDTO.PasswordResetResponse response) {
        if (response.resetTokenPreview() == null || response.resetTokenPreview().isBlank()) {
            return "임시 비밀번호를 이메일로 전송했습니다. 로그인 후 바로 비밀번호를 변경해 주세요.";
        }
        return "임시 비밀번호를 이메일로 전송했습니다. 메일 설정이 없어 임시 비밀번호를 함께 표시합니다: "
                + response.resetTokenPreview();
    }
}
