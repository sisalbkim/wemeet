package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.ViewModeSupport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.ui.Model;

/**
 * ViewModeModelAdvice는 화면 요청과 API 요청을 받아 서비스 계층으로 위임하는 MVC 컨트롤러입니다.
 */
@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class ViewModeModelAdvice {

    private final ViewModeSupport viewModeSupport;

    @ModelAttribute
    public void populateViewMode(Model model, HttpServletRequest request) {
        model.addAttribute("viewMode", viewModeSupport.resolve(request));
    }
}
