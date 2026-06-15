package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.HistoryDTO;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IHistoryService;
import com.kopo.wemeet.service.IWemeetViewService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HistoryController {
    // 사용자 검색 기록 조회와 삭제 화면 흐름을 처리하는 컨트롤러.

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final IHistoryService historyService;
    private final WemeetViewHelper viewHelper;

    @GetMapping("/history")
    public String history(
            @RequestParam(defaultValue = "전체") String filter,
            @RequestParam(defaultValue = "") String keyword,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        viewHelper.populateCommon(model, "history", false);
        model.addAttribute("selectedFilter", filter);
        model.addAttribute("keyword", keyword);
        model.addAttribute("categories", viewService.getCategories());
        model.addAttribute("searchHistory", viewService.getSearchHistory(currentUser.getId(), filter, keyword));
        return "history/index";
    }

    @PostMapping("/history/clear")
    public String clearHistory(
            @RequestParam(defaultValue = "전체") String filter,
            @RequestParam(defaultValue = "") String keyword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        viewService.clearSearchHistory(currentUser.getId());
        redirectAttributes.addAttribute("filter", filter);
        redirectAttributes.addAttribute("keyword", keyword);
        return "redirect:/history";
    }

    @PostMapping("/history/remove")
    public String removeHistory(
            @RequestParam Long historyId,
            @RequestParam(defaultValue = "전체") String filter,
            @RequestParam(defaultValue = "") String keyword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        viewService.removeSearchHistory(currentUser.getId(), historyId);
        redirectAttributes.addAttribute("filter", filter);
        redirectAttributes.addAttribute("keyword", keyword);
        return "redirect:/history";
    }

    @ResponseBody
    @GetMapping("/api/history")
    public List<HistoryDTO.SearchHistoryResponse> apiHistory(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @CookieValue(name = "${app.auth.jwt.access-cookie-name:WM_ACCESS_TOKEN}", required = false) String accessToken
    ) {
        AppUser requester = authService.requireUser(authorization, accessToken);
        return historyService.listHistory(requester.getId()).stream()
                .map(entry -> new HistoryDTO.SearchHistoryResponse(
                        entry.query(),
                        entry.category(),
                        entry.searchedAt().toString()
                ))
                .toList();
    }
}
