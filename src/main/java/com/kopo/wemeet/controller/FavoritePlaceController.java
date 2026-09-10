package com.kopo.wemeet.controller;

import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.impl.FavoritePlaceService;
import com.kopo.wemeet.util.WemeetViewHelper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class FavoritePlaceController {

    private final FavoritePlaceService favoritePlaceService;
    private final WemeetViewHelper viewHelper;

    // 검색 결과의 장소를 즐겨찾기에 저장
    @PostMapping("/favorites")
    public String addFavorite(
            @RequestParam String name,
            @RequestParam String category,
            @RequestParam String address,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(required = false) String redirectUrl,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        favoritePlaceService.addFavorite(
                currentUser.getId(),
                name,
                category,
                address,
                latitude,
                longitude
        );

        // 즐겨찾기 저장 후 기존 검색 결과 페이지로 돌아가기
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + redirectUrl;
        }

        return "redirect:/search/results";
    }
}