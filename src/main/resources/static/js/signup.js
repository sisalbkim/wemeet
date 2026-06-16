// 회원가입 이메일 인증 흐름만 따로 관리한다.
document.addEventListener("DOMContentLoaded", () => {
    // 회원가입 화면에서 이메일 중복확인/인증코드 발송/인증 완료 상태를 관리한다.
    const signupForm = document.querySelector("[data-signup-form]");
    if (signupForm instanceof HTMLFormElement) {
        const emailInput = signupForm.querySelector("#email");
        const codeInput = signupForm.querySelector("#emailVerificationCode");
        const userIdInput = signupForm.querySelector("#userId");
        const userIdCheckButton = signupForm.querySelector("[data-user-id-check-button]");
        const emailCheckButton = signupForm.querySelector("[data-email-check-button]");
        const emailSendButton = signupForm.querySelector("[data-email-send-button]");
        const emailVerifyButton = signupForm.querySelector("[data-email-verify-button]");
        const userIdCheckFeedback = signupForm.querySelector("[data-user-id-check-feedback]");
        const emailCheckFeedback = signupForm.querySelector("[data-email-check-feedback]");
        const emailVerifyFeedback = signupForm.querySelector("[data-email-verify-feedback]");
        const defaultSendButtonText = emailSendButton instanceof HTMLButtonElement ? emailSendButton.textContent : "";

        let verifiedEmail = String(signupForm.dataset.verifiedEmail || "").trim().toLowerCase();
        let verifiedUserId = String(signupForm.dataset.verifiedUserId || "").trim().toLowerCase();

        const setFeedback = (node, tone, message) => {
            if (!(node instanceof HTMLElement)) {
                return;
            }
            node.textContent = message;
            node.classList.remove("is-success", "is-error");
            if (tone) {
                node.classList.add(tone === "success" ? "is-success" : "is-error");
            }
        };

        const localizeSignupMessage = (message) => {
            const normalizedMessage = String(message || "").trim();
            const messageMap = {
                "loginId is required": "아이디를 입력해주세요.",
                "email already exists": "이미 사용 중인 이메일입니다.",
                "email is required": "이메일을 입력해주세요.",
                "invalid email format": "올바른 이메일 형식을 입력해주세요.",
                "먼저 해당 이메일로 인증코드를 전송해주세요.": "먼저 이메일 중복확인을 하고 인증코드를 전송해주세요."
            };
            return messageMap[normalizedMessage] || normalizedMessage || "요청을 처리하지 못했습니다.";
        };

        const currentUserId = () => userIdInput instanceof HTMLInputElement ? userIdInput.value.trim() : "";
        const currentEmail = () => emailInput instanceof HTMLInputElement ? emailInput.value.trim() : "";

        const validateUserIdInput = () => {
            if (!(userIdInput instanceof HTMLInputElement)) {
                return false;
            }
            if (!userIdInput.reportValidity()) {
                return false;
            }
            return true;
        };

        const validateEmailInput = () => {
            if (!(emailInput instanceof HTMLInputElement)) {
                return false;
            }
            if (!emailInput.reportValidity()) {
                return false;
            }
            return true;
        };

        const clearVerificationState = () => {
            verifiedEmail = "";
            if (emailSendButton instanceof HTMLButtonElement) {
                emailSendButton.textContent = defaultSendButtonText || "이메일 전송";
            }
            setFeedback(emailVerifyFeedback, "", "이메일 인증이 필요합니다.");
        };

        const clearUserIdVerificationState = () => {
            verifiedUserId = "";
            setFeedback(userIdCheckFeedback, "", "아이디 중복 여부를 먼저 확인해주세요.");
        };

        if (userIdInput instanceof HTMLInputElement) {
            userIdInput.addEventListener("input", clearUserIdVerificationState);
        }

        if (userIdCheckButton instanceof HTMLButtonElement) {
            userIdCheckButton.addEventListener("click", async () => {
                if (!validateUserIdInput()) {
                    return;
                }
                const userId = currentUserId();
                try {
                    const response = await fetch(`/api/auth/user-id/available?userId=${encodeURIComponent(userId)}`);
                    const result = await response.json();
                    if (result.available) {
                        verifiedUserId = userId.toLowerCase();
                    } else {
                        verifiedUserId = "";
                    }
                    setFeedback(
                        userIdCheckFeedback,
                        result.available ? "success" : "error",
                        localizeSignupMessage(result.message)
                    );
                } catch (error) {
                    console.warn("User ID availability check failed", error);
                    setFeedback(userIdCheckFeedback, "error", "아이디 중복확인 중 오류가 발생했습니다.");
                }
            });
        }

        if (emailInput instanceof HTMLInputElement) {
            emailInput.addEventListener("input", () => {
                clearVerificationState();
                setFeedback(emailCheckFeedback, "", "이메일 중복 여부를 먼저 확인해주세요.");
            });
        }

        if (emailCheckButton instanceof HTMLButtonElement) {
            emailCheckButton.addEventListener("click", async () => {
                if (!validateEmailInput()) {
                    return;
                }
                const email = currentEmail();
                try {
                    const response = await fetch(`/api/auth/email/available?email=${encodeURIComponent(email)}`);
                    const result = await response.json();
                    setFeedback(
                        emailCheckFeedback,
                        result.available ? "success" : "error",
                        localizeSignupMessage(result.message)
                    );
                } catch (error) {
                    console.warn("Email availability check failed", error);
                    setFeedback(emailCheckFeedback, "error", "이메일 중복확인 중 오류가 발생했습니다.");
                }
            });
        }

        if (emailSendButton instanceof HTMLButtonElement) {
            emailSendButton.addEventListener("click", async () => {
                if (!validateEmailInput()) {
                    return;
                }
                const email = currentEmail();
                try {
                    const response = await fetch("/api/auth/email/send-code", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ email })
                    });
                    const result = await response.json();
                    setFeedback(
                        emailVerifyFeedback,
                        result.sent ? "success" : "error",
                        result.sent
                            ? (result.codePreview ? `${result.message} 인증코드: ${result.codePreview}` : result.message)
                            : localizeSignupMessage(result.message)
                    );
                    if (result.sent) {
                        emailSendButton.textContent = "이메일 재전송";
                    }
                    verifiedEmail = "";
                } catch (error) {
                    console.warn("Email verification send failed", error);
                    setFeedback(emailVerifyFeedback, "error", "인증코드 전송 중 오류가 발생했습니다.");
                }
            });
        }

        if (emailVerifyButton instanceof HTMLButtonElement) {
            emailVerifyButton.addEventListener("click", async () => {
                if (!validateEmailInput()) {
                    return;
                }
                const email = currentEmail();
                const code = codeInput instanceof HTMLInputElement ? codeInput.value.trim() : "";
                if (!code) {
                    setFeedback(emailVerifyFeedback, "error", "이메일과 인증코드를 모두 입력해주세요.");
                    return;
                }
                try {
                    const response = await fetch("/api/auth/email/verify-code", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ email, code })
                    });
                    const result = await response.json();
                    if (result.verified) {
                        verifiedEmail = email.toLowerCase();
                    }
                    setFeedback(
                        emailVerifyFeedback,
                        result.verified ? "success" : "error",
                        localizeSignupMessage(result.message)
                    );
                } catch (error) {
                    console.warn("Email verification confirm failed", error);
                    setFeedback(emailVerifyFeedback, "error", "이메일 인증 확인 중 오류가 발생했습니다.");
                }
            });
        }

        if (verifiedEmail && verifiedEmail === currentEmail().toLowerCase()) {
            setFeedback(emailVerifyFeedback, "success", "이메일 인증이 완료된 상태입니다.");
        }

        signupForm.addEventListener("submit", (event) => {
            if (verifiedUserId !== currentUserId().toLowerCase()) {
                event.preventDefault();
                setFeedback(userIdCheckFeedback, "error", "아이디 중복확인을 먼저 완료해주세요.");
                return;
            }
            if (verifiedEmail !== currentEmail().toLowerCase()) {
                event.preventDefault();
                setFeedback(emailVerifyFeedback, "error", "이메일 중복확인과 이메일 인증을 먼저 완료해주세요.");
            }
        });
    }

});
