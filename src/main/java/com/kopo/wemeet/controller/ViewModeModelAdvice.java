package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.ViewModeSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.ui.Model;

@ControllerAdvice(annotations = Controller.class)
public class ViewModeModelAdvice {

    private final ViewModeSupport viewModeSupport;

    public ViewModeModelAdvice(ViewModeSupport viewModeSupport) {
        this.viewModeSupport = viewModeSupport;
    }

    @ModelAttribute
    public void populateViewMode(Model model, HttpServletRequest request) {
        model.addAttribute("viewMode", viewModeSupport.resolve(request));
    }
}
