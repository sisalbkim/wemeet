package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.ViewModeSupport;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
