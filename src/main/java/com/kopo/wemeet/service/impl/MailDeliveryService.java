package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.AppMailProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class MailDeliveryService {
    // 인증코드와 임시 비밀번호 메일을 SMTP로 발송하고, 개발 환경에서는 preview fallback도 제공한다.

    private static final Logger log = LoggerFactory.getLogger(MailDeliveryService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppMailProperties mailProperties;

    public MailDeliveryService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            AppMailProperties mailProperties
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailProperties = mailProperties;
    }

    public MailSendResult sendSignupVerificationCode(String email, String code) {
        if (!mailProperties.isEnabled()) {
            return previewFallback(email, code, "메일 설정이 없어 화면용 인증코드를 함께 반환합니다.");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return new MailSendResult(false, "메일 발송기가 등록되지 않았습니다. SMTP 설정을 확인해주세요.", null);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            if (mailProperties.getFromAddress() != null && !mailProperties.getFromAddress().isBlank()) {
                helper.setFrom(new InternetAddress(
                        mailProperties.getFromAddress().trim(),
                        mailProperties.getFromName(),
                        StandardCharsets.UTF_8.name()
                ));
            }
            helper.setTo(email);
            message.setSubject(mailProperties.getSignupVerificationSubject(), StandardCharsets.UTF_8.name());
            helper.setText(buildSignupVerificationBody(code), false);
            mailSender.send(message);
            return new MailSendResult(true, "인증코드를 이메일로 전송했습니다.", null);
        } catch (Exception exception) {
            log.error("Failed to send signup verification email to {}", email, exception);
            return new MailSendResult(false, "인증코드 메일 전송에 실패했습니다. SMTP 설정을 확인해주세요.", null);
        }
    }

    public MailSendResult sendTemporaryPassword(String email, String loginId, String temporaryPassword) {
        if (!mailProperties.isEnabled()) {
            return previewFallback(email, temporaryPassword, "메일 설정이 없어 임시 비밀번호를 함께 반환합니다.");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return new MailSendResult(false, "메일 발송기가 등록되지 않았습니다. SMTP 설정을 확인해주세요.", null);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            if (mailProperties.getFromAddress() != null && !mailProperties.getFromAddress().isBlank()) {
                helper.setFrom(new InternetAddress(
                        mailProperties.getFromAddress().trim(),
                        mailProperties.getFromName(),
                        StandardCharsets.UTF_8.name()
                ));
            }
            helper.setTo(email);
            message.setSubject("[WeMeet] 임시 비밀번호 안내", StandardCharsets.UTF_8.name());
            helper.setText(buildTemporaryPasswordBody(loginId, temporaryPassword), false);
            mailSender.send(message);
            return new MailSendResult(true, "임시 비밀번호를 이메일로 전송했습니다.", null);
        } catch (Exception exception) {
            log.error("Failed to send temporary password email to {}", email, exception);
            return new MailSendResult(false, "임시 비밀번호 메일 전송에 실패했습니다. SMTP 설정을 확인해주세요.", null);
        }
    }

    private MailSendResult previewFallback(String email, String code, String message) {
        if (!mailProperties.isPreviewFallbackEnabled()) {
            return new MailSendResult(false, "메일 설정이 없어 인증코드를 발송할 수 없습니다.", null);
        }

        log.info("Mail preview fallback for signup verification. email={}, code={}", email, code);
        return new MailSendResult(true, message, code);
    }

    private String buildSignupVerificationBody(String code) {
        return """
                WeMeet 이메일 인증코드입니다.

                인증코드: %s

                회원가입 화면으로 돌아가 인증코드를 입력해 주세요.
                """.formatted(code);
    }

    private String buildTemporaryPasswordBody(String loginId, String temporaryPassword) {
        return """
                WeMeet 임시 비밀번호 안내입니다.

                아이디: %s
                임시 비밀번호: %s

                로그인 후 바로 비밀번호를 변경해 주세요.
                """.formatted(loginId, temporaryPassword);
    }

    public record MailSendResult(
            boolean sent,
            String message,
            String previewCode
    ) {
    }
}
