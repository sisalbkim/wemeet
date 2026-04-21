package com.kopo.wemeet.util;

import com.kopo.wemeet.config.NaverMapProperties;
import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

@Component
public class WemeetViewHelper {

    private final IApiAuthService authService;
    private final NaverMapProperties naverMapProperties;

    public WemeetViewHelper(IApiAuthService authService, NaverMapProperties naverMapProperties) {
        this.authService = authService;
        this.naverMapProperties = naverMapProperties;
    }

    public void populateCommon(Model model, String activeTab, boolean guestMode) {
        model.addAttribute("appName", "모임 장소 찾기");
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("guestMode", guestMode);
        model.addAttribute("naverMapEnabled", naverMapProperties.isEnabled());
        model.addAttribute("naverMapKeyId", naverMapProperties.getKeyId());
    }

    public AppUser findLoggedInUser(HttpSession session) {
        if (session == null) {
            return null;
        }

        String token = (String) session.getAttribute("AUTH_TOKEN");
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            return authService.requireUser("Bearer " + token);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 401) {
                session.invalidate();
                return null;
            }
            throw exception;
        }
    }

    public AppUser requireLoggedInUser(HttpSession session) {
        AppUser user = findLoggedInUser(session);
        if (user == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Login required");
        }
        return user;
    }

    public UserDTO.UserProfile toProfile(AppUser user) {
        return new UserDTO.UserProfile(
                user.getId(),
                user.getNickname(),
                "@" + user.getLoginId(),
                user.getFriendCode(),
                user.getBaseAddress(),
                "••••••••",
                2,
                2,
                3
        );
    }
}
