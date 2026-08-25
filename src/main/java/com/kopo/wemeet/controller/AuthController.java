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
    // 로그인, 회원가입, 아이디/비밀번호 찾기처럼 "사용자 인증"과 관련된 화면 흐름을 처리하는 컨트롤러입니다.
    // 컨트롤러는 직접 DB를 다루지 않고, 필요한 실제 작업은 authService 같은 서비스 클래스에 맡깁니다.

    private final IApiAuthService authService;
    private final WemeetViewHelper viewHelper;
    private final MailDeliveryService mailDeliveryService;

    // 로그인 화면을 보여주는 기능입니다.
    // 주소창에서 /login으로 들어오면 공통 화면 정보와 로그인 실패 여부(error)를 모델에 담아 login.html로 보냅니다.
    @GetMapping("/login")
    public String login(
            @RequestParam(defaultValue = "false") boolean error,
            Model model
    ) {
        viewHelper.populateCommon(model, "login", true);
        model.addAttribute("error", error);
        return "auth/login";
    }

    // 로그인 폼을 제출했을 때 실행되는 기능입니다.
    // 사용자가 입력한 아이디/비밀번호를 서비스에 넘겨 로그인하고, 성공하면 인증 토큰을 쿠키에 저장한 뒤 메인 화면으로 이동합니다.
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

    // 회원가입 화면을 보여주는 기능입니다.
    // 이메일 인증이 이미 끝난 상태인지도 함께 모델에 담아, 화면에서 인증 완료 여부를 표시할 수 있게 합니다.
    @GetMapping("/signup")
    public String signup(Model model, HttpSession session) {
        populateSignupModel(model, session);
        return "auth/signup";
    }

    // 회원가입 폼을 제출했을 때 실행되는 기능입니다.
    // 비밀번호 확인, 이메일 인증 여부를 먼저 검사하고, 문제가 없으면 서비스에 회원가입 처리를 요청합니다.
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

        // 회원가입 실패 시 사용자가 입력했던 값을 다시 화면에 보여주기 위한 공통 처리입니다.
        // redirect를 하면 Model 값은 사라지기 때문에 RedirectAttributes의 flashAttribute를 사용합니다.
        Function<String, String> redirectSignupError = message -> {
            redirectAttributes.addFlashAttribute("signupError", message);
            redirectAttributes.addFlashAttribute("signupNickname", nickname);
            redirectAttributes.addFlashAttribute("signupUserId", normalizedUserId);
            redirectAttributes.addFlashAttribute("signupEmail", normalizedEmail);
            redirectAttributes.addFlashAttribute("signupBaseAddress", baseAddress);
            return "redirect:/signup";
        };

        // 비밀번호와 비밀번호 확인 값이 다르면 회원가입을 진행하지 않고 다시 회원가입 화면으로 돌려보냅니다.
        if (!password.equals(confirmPassword)) {
            return redirectSignupError.apply("비밀번호 확인이 일치하지 않습니다.");
        }

        // 세션에 저장된 인증 완료 이메일과 현재 입력한 이메일이 같아야 회원가입을 허용합니다.
        // 이렇게 해야 다른 이메일로 인증한 뒤 가입 이메일만 바꿔 넣는 상황을 막을 수 있습니다.
        String verifiedEmail = (String) session.getAttribute("SIGNUP_VERIFIED_EMAIL");
        if (verifiedEmail == null || !verifiedEmail.equalsIgnoreCase(normalizedEmail)) {
            return redirectSignupError.apply("이메일 중복확인과 이메일 인증을 먼저 완료해주세요.");
        }

        // 실제 회원가입 저장은 authService가 담당합니다.
        // 서비스에서 중복 아이디/이메일 같은 문제가 발생하면 예외를 화면용 메시지로 바꿔 보여줍니다.
        try {
            authService.signUp(createSignUpRequest(nickname, normalizedUserId, password, normalizedEmail, baseAddress));
        } catch (ResponseStatusException exception) {
            return redirectSignupError.apply(toSignupErrorMessage(exception));
        }

        // 회원가입이 끝났으므로 세션에 남아 있던 이메일 인증 정보를 지웁니다.
        clearSignupVerification(session);

        redirectAttributes.addFlashAttribute("signupSuccess", true);
        redirectAttributes.addFlashAttribute("registeredNickname", nickname.isBlank() ? normalizedUserId : nickname);
        return "redirect:/login";
    }

    // 회원가입 화면에서 아이디 중복 여부를 확인하는 API입니다.
    // @ResponseBody가 붙어 있어서 HTML 화면 이름이 아니라 JSON 응답 객체가 브라우저로 전달됩니다.
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

    // 아이디 찾기 화면을 보여주는 기능입니다.
    @GetMapping("/find-id")
    public String findId(Model model) {
        populateFindIdModel(model);
        return "auth/find-id";
    }

    // 아이디 찾기 폼을 제출했을 때 실행되는 기능입니다.
    // 이름과 이메일로 계정을 조회하고, 찾은 아이디 또는 실패 메시지를 다음 화면에 전달합니다.
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

    // 비밀번호 찾기 화면을 보여주는 기능입니다.
    @GetMapping("/find-password")
    public String findPassword(Model model) {
        populateFindPasswordModel(model);
        return "auth/find-password";
    }

    // 비밀번호 찾기에서 아이디와 이메일을 확인한 뒤 임시 비밀번호를 발급하는 기능입니다.
    // 성공하면 로그인 화면으로 보내고, 실패하면 사용자가 다시 입력할 수 있도록 비밀번호 찾기 화면으로 돌려보냅니다.
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

    // 로그아웃 기능입니다.
    // 저장되어 있던 refresh token을 무효화하고, 브라우저의 인증 쿠키와 서버 세션을 정리한 뒤 메인 화면으로 이동합니다.
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

    // 회원가입 화면에서 이메일 중복 여부를 확인하는 API입니다.
    // 사용 가능한 이메일인지 검사한 결과와 화면에 보여줄 메시지를 JSON으로 반환합니다.
    @ResponseBody
    @GetMapping("/api/auth/email/available")
    public AuthDTO.EmailAvailabilityResponse apiEmailAvailable(@RequestParam String email) {
        boolean available = authService.isEmailAvailable(email);
        return new AuthDTO.EmailAvailabilityResponse(
                available,
                available ? "사용 가능한 이메일입니다." : "이미 사용 중인 이메일입니다."
        );
    }

    // 회원가입 이메일 인증코드를 발송하는 API입니다.
    // 인증코드를 만들고 메일로 보낸 뒤, 사용자가 나중에 입력한 코드와 비교할 수 있도록 세션에 저장합니다.
    @ResponseBody
    @PostMapping("/api/auth/email/send-code")
    public AuthDTO.EmailVerificationSendResponse apiSendEmailVerificationCode(
            @RequestBody AuthDTO.EmailVerificationSendRequest request,
            HttpSession session
    ) {
        String normalizedEmail = CmmUtil.nvl(request.email());
        String code = authService.createSignupEmailVerificationCode(normalizedEmail);
        MailDeliveryService.MailSendResult mailSendResult = mailDeliveryService.sendSignupVerificationCode(normalizedEmail, code);
        // 메일 발송에 성공한 경우에만 세션에 인증 대기 상태를 저장합니다.
        // 실패했다면 이전 인증 정보가 남아 혼동되지 않도록 모두 지웁니다.
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

    // 사용자가 입력한 이메일 인증코드를 확인하는 API입니다.
    // 세션에 저장된 이메일/코드와 요청값이 모두 일치하면 해당 이메일을 "인증 완료" 상태로 저장합니다.
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

        // 이메일이나 코드가 비어 있으면 비교할 수 없으므로 바로 실패 응답을 보냅니다.
        if (normalizedEmail.isBlank() || inputCode.isBlank()) {
            return new AuthDTO.EmailVerificationConfirmResponse(false, "이메일과 인증코드를 입력해주세요.");
        }
        // 코드 발송을 먼저 하지 않았거나, 발송한 이메일과 현재 이메일이 다르면 인증을 허용하지 않습니다.
        if (!normalizedEmail.equalsIgnoreCase(pendingEmail) || pendingCode == null) {
            return new AuthDTO.EmailVerificationConfirmResponse(false, "먼저 해당 이메일로 인증코드를 전송해주세요.");
        }
        // 사용자가 입력한 코드가 세션에 저장된 코드와 정확히 같아야 인증 성공입니다.
        if (!pendingCode.equals(inputCode)) {
            return new AuthDTO.EmailVerificationConfirmResponse(false, "인증코드가 일치하지 않습니다.");
        }

        session.setAttribute("SIGNUP_VERIFIED_EMAIL", normalizedEmail);
        return new AuthDTO.EmailVerificationConfirmResponse(true, "이메일 인증이 완료되었습니다.");
    }

    // 회원가입 화면에 필요한 공통 모델 값을 채우는 내부 헬퍼 메서드입니다.
    // 여러 곳에서 반복될 수 있는 화면 준비 코드를 한곳에 모아 둔 것입니다.
    private void populateSignupModel(Model model, HttpSession session) {
        viewHelper.populateCommon(model, "login", true);
        model.addAttribute("signupVerifiedEmail", session.getAttribute("SIGNUP_VERIFIED_EMAIL"));
    }

    // 비밀번호 찾기 화면에 공통 레이아웃 정보를 넣는 내부 헬퍼 메서드입니다.
    private void populateFindPasswordModel(Model model) {
        viewHelper.populateCommon(model, "login", true);
    }

    // 아이디 찾기 화면에 공통 레이아웃 정보를 넣는 내부 헬퍼 메서드입니다.
    private void populateFindIdModel(Model model) {
        viewHelper.populateCommon(model, "login", true);
    }

    // 로그인 서비스 호출과 쿠키 저장을 한 번에 처리하는 내부 헬퍼 메서드입니다.
    // 로그인 성공 결과로 받은 토큰을 쿠키에 적어야 이후 요청에서도 로그인 상태를 유지할 수 있습니다.
    private AuthDTO.AuthResponse loginAndWriteCookies(
            AuthDTO.LoginRequest request,
            HttpServletResponse response,
            boolean persistent
    ) {
        AuthDTO.AuthResponse authResponse = authService.login(request);
        authService.writeTokenCookies(response, authResponse, persistent);
        return authResponse;
    }

    // 회원가입 서비스에 넘길 요청 DTO를 만드는 내부 헬퍼 메서드입니다.
    // 컨트롤러에서 받은 여러 입력값을 하나의 객체로 묶어 서비스에 전달합니다.
    private AuthDTO.SignUpRequest createSignUpRequest(
            String nickname,
            String loginId,
            String password,
            String email,
            String baseAddress
    ) {
        return new AuthDTO.SignUpRequest(nickname, loginId, password, email, baseAddress);
    }

    // 회원가입 이메일 인증과 관련된 세션 값을 모두 삭제하는 내부 헬퍼 메서드입니다.
    // 가입 완료 또는 메일 발송 실패처럼 인증 상태를 초기화해야 할 때 사용합니다.
    private void clearSignupVerification(HttpSession session) {
        session.removeAttribute("SIGNUP_VERIFICATION_EMAIL");
        session.removeAttribute("SIGNUP_VERIFICATION_CODE");
        session.removeAttribute("SIGNUP_VERIFIED_EMAIL");
    }

    // 서비스에서 발생한 회원가입 예외 메시지를 사용자가 이해하기 쉬운 한국어 문구로 바꾸는 메서드입니다.
    private String toSignupErrorMessage(ResponseStatusException exception) {
        return switch (exception.getReason()) {
            case "email already exists" -> "이미 사용 중인 이메일입니다.";
            case "loginId already exists" -> "이미 사용 중인 아이디입니다.";
            case "nickname, loginId, password, and email are required" -> "필수 입력값을 모두 작성해주세요.";
            default -> exception.getReason();
        };
    }

    // 임시 비밀번호 발급 성공 시 화면에 보여줄 안내 문구를 만드는 메서드입니다.
    // 메일 설정이 없어서 미리보기 값이 있는 경우에는 개발/테스트용으로 임시 비밀번호를 함께 보여줍니다.
    private String buildTemporaryPasswordNotice(AuthDTO.PasswordResetResponse response) {
        if (response.resetTokenPreview() == null || response.resetTokenPreview().isBlank()) {
            return "임시 비밀번호를 이메일로 전송했습니다. 로그인 후 바로 비밀번호를 변경해 주세요.";
        }
        return "임시 비밀번호를 이메일로 전송했습니다. 메일 설정이 없어 임시 비밀번호를 함께 표시합니다: "
                + response.resetTokenPreview();
    }
}
