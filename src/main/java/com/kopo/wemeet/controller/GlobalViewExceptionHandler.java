package com.kopo.wemeet.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@ControllerAdvice
public class GlobalViewExceptionHandler {
    // 화면 요청에서 생긴 예외를 사용자용 에러 화면이나 리다이렉트로 정리한.

    @ExceptionHandler(ResponseStatusException.class)
    public Object handleResponseStatusException(
            ResponseStatusException exception,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        if (request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity
                    .status(exception.getStatusCode())
                    .body(Map.of("error", exception.getReason() == null ? "Request failed" : exception.getReason()));
        }

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
}
