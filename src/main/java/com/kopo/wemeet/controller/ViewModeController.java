package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.ViewModeSupport;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ViewModeController는 화면 요청과 API 요청을 받아 서비스 계층으로 위임하는 MVC 컨트롤러입니다.
 */
@Controller
@RequiredArgsConstructor
public class ViewModeController {

    private final ViewModeSupport viewModeSupport;

    @GetMapping("/view-mode")
    public String updateViewMode(
            @RequestParam(defaultValue = ViewModeSupport.MOBILE) String mode,
            @RequestParam(defaultValue = "/") String redirect,
            HttpServletResponse response
    ) {
        viewModeSupport.write(response, mode);
        return "redirect:" + viewModeSupport.sanitizeRedirectTarget(redirect);
    }
}
