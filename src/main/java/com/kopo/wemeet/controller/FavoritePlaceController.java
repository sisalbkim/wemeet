package com.kopo.wemeet.controller;

import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.impl.FavoritePlaceService;
import com.kopo.wemeet.util.WemeetViewHelper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class FavoritePlaceController {

    private final FavoritePlaceService favoritePlaceService;
    private final WemeetViewHelper viewHelper;

    // 즐겨찾기 등록 / 해제
    @PostMapping("/favorites")
    @ResponseBody
    public boolean toggleFavorite(
            @RequestParam String name,
            @RequestParam String category,
            @RequestParam String address,
            @RequestParam double latitude,
            @RequestParam double longitude,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        return favoritePlaceService.toggleFavorite(
                currentUser.getId(),
                name,
                category,
                address,
                latitude,
                longitude
        );
    }


    @GetMapping("/favorites/check")
    @ResponseBody
    public boolean checkFavorite(
            @RequestParam double latitude,
            @RequestParam double longitude,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        return favoritePlaceService.isFavorite(
                currentUser.getId(),
                latitude,
                longitude
        );
    }

    // 즐겨찾기 목록 페이지
    @GetMapping("/favorites")
    public String favorites(
            HttpSession session,
            Model model
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        model.addAttribute(
                "favorites",
                favoritePlaceService.listFavorites(currentUser.getId())
        );

        model.addAttribute("activeTab", "favorites");

        return "favorites/index";
    }

    // 즐겨찾기 개별 삭제
    @PostMapping("/favorites/remove")
    public String removeFavorite(
            @RequestParam Long favoriteId,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        favoritePlaceService.removeFavorite(
                currentUser.getId(),
                favoriteId
        );

        return "redirect:/favorites";
    }

    // 즐겨찾기 전체 삭제
    @PostMapping("/favorites/clear")
    public String clearFavorites(HttpSession session) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);

        favoritePlaceService.clearFavorites(currentUser.getId());

        return "redirect:/favorites";
    }
}