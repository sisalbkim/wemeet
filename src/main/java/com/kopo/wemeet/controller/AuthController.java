package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.*;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IWemeetViewService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.function.Function;

@Controller
@RequiredArgsConstructor
public class AuthController {
    // 로그인, 회원가입, 비밀번호 찾기 같은 인증 화면 흐름을 처리하는 컨트롤러.

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final WemeetViewHelper viewHelper;
    private final MailDeliveryService mailDeliveryService;

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

    @PostMapping("/login")
    public String loginSubmit(
            @RequestParam(name = "userId", defaultValue = "") String userId,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "false") boolean autoLogin,
            HttpServletResponse response
    ) {
        String normalizedUserId = CmmUtil.nvl(userId);
        AuthDTO.AuthResponse loginResult = authService.login(new AuthDTO.LoginRequest(normalizedUserId, password));

        /*
         * 로그인 성공 후 세션에 AUTH_TOKEN/USER_ID를 저장하지 않는다.
         * Access/Refresh Token을 HttpOnly 쿠키로 내려주고, 이후 요청은 Access Token 쿠키로 인증한다.
         *
         * autoLogin=true  -> 브라우저를 닫아도 유지되는 persistent cookie
         * autoLogin=false -> 브라우저 세션 동안만 유지되는 session cookie
         */
        authService.writeTokenCookies(response, loginResult, autoLogin);
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
            RedirectAttributes redirectAttributes
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
            authService.signUp(new AuthDTO.SignUpRequest(
                    nickname,
                    normalizedUserId,
                    password,
                    normalizedEmail,
                    baseAddress
            ));
        } catch (ResponseStatusException exception) {
            return redirectSignupError.apply(switch (exception.getReason()) {
                case "email already exists" -> "이미 사용 중인 이메일입니다.";
                case "loginId already exists" -> "이미 사용 중인 아이디입니다.";
                case "nickname, loginId, password, and email are required" -> "필수 입력값을 모두 작성해주세요.";
                default -> exception.getReason();
            });
        }

        session.removeAttribute("SIGNUP_VERIFICATION_EMAIL");
        session.removeAttribute("SIGNUP_VERIFICATION_CODE");
        session.removeAttribute("SIGNUP_VERIFIED_EMAIL");

        redirectAttributes.addAttribute("registered", true);
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
    @PostMapping("/api/auth/signup")
    public AuthDTO.AuthResponse apiSignUp(@RequestBody AuthDTO.SignUpRequest request, HttpServletResponse response) {
        AuthDTO.AuthResponse authResponse = authService.signUp(request);
        authService.writeTokenCookies(response, authResponse);
        return authResponse;
    }

    @ResponseBody
    @PostMapping("/api/auth/login")
    public AuthDTO.AuthResponse apiLogin(@RequestBody AuthDTO.LoginRequest request, HttpServletResponse response) {
        AuthDTO.AuthResponse authResponse = authService.login(request);
        authService.writeTokenCookies(response, authResponse);
        return authResponse;
    }

    @ResponseBody
    @PostMapping("/api/auth/refresh")
    public AuthDTO.AuthResponse apiRefresh(
            @RequestBody(required = false) AuthDTO.RefreshRequest request,
            @CookieValue(name = "${app.auth.jwt.refresh-cookie-name:WM_REFRESH_TOKEN}", required = false) String refreshTokenCookie,
            HttpServletResponse response
    ) {
        AuthDTO.AuthResponse authResponse = authService.refreshAccessToken(resolveRefreshToken(request, refreshTokenCookie));
        authService.writeTokenCookies(response, authResponse);
        return authResponse;
    }

    @ResponseBody
    @PostMapping("/api/auth/logout")
    public AuthDTO.PasswordResetResponse apiLogout(
            @CookieValue(name = "${app.auth.jwt.refresh-cookie-name:WM_REFRESH_TOKEN}", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.revokeRefreshToken(refreshToken);
        authService.clearTokenCookies(response);
        return new AuthDTO.PasswordResetResponse("로그아웃되었습니다.", null);
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
    public UserDTO.UserResponse apiMe(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @CookieValue(name = "${app.auth.jwt.access-cookie-name:WM_ACCESS_TOKEN}", required = false) String accessToken
    ) {
        return authService.toUserResponse(authService.requireUser(authorization, accessToken));
    }

    @ResponseBody
    @PostMapping("/api/me/address")
    public UserDTO.UserResponse apiUpdateAddress(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @CookieValue(name = "${app.auth.jwt.access-cookie-name:WM_ACCESS_TOKEN}", required = false) String accessToken,
            @RequestBody UserDTO.AddressUpdateRequest request
    ) {
        AppUser requester = authService.requireUser(authorization, accessToken);
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

    private String resolveRefreshToken(AuthDTO.RefreshRequest request, String refreshTokenCookie) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        return refreshTokenCookie;
    }

    private String buildTemporaryPasswordNotice(AuthDTO.PasswordResetResponse response) {
        if (response.resetTokenPreview() == null || response.resetTokenPreview().isBlank()) {
            return "임시 비밀번호를 이메일로 전송했습니다. 로그인 후 바로 비밀번호를 변경해 주세요.";
        }
        return "임시 비밀번호를 이메일로 전송했습니다. 메일 설정이 없어 임시 비밀번호를 함께 표시합니다: "
                + response.resetTokenPreview();
    }
}
