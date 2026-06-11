package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.*;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IFriendService;
import com.kopo.wemeet.service.IWemeetViewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.IntStream;

@Controller
public class FriendController {
    // 친구 목록, 친구 요청, 친구 추가와 승인 흐름을 담당하는 컨트롤러.

    private static final int FRIEND_PAGE_SIZE = 10;

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final IFriendService friendService;
    private final WemeetViewHelper viewHelper;

    public FriendController(
            IWemeetViewService viewService,
            IApiAuthService authService,
            IFriendService friendService,
            WemeetViewHelper viewHelper
    ) {
        this.viewService = viewService;
        this.authService = authService;
        this.friendService = friendService;
        this.viewHelper = viewHelper;
    }

    @GetMapping("/friends")
    public String friends(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            Model model,
            HttpSession session
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        List<FriendDTO.FriendSummary> friends = viewService.getFriends(currentUser.getId(), keyword);
        populateFriendsModel(model, currentUser.getId(), viewHelper.toProfile(currentUser), friends, keyword, page);
        return "friends/index";
    }

    @PostMapping("/friends/add")
    public String addFriend(
            @RequestParam(defaultValue = "") String friendCode,
            @RequestParam(defaultValue = "/friends") String redirectTo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        try {
            UserDTO.UserResponse requestedFriend = viewService.addFriendByCode(currentUser.getId(), friendCode);
            redirectAttributes.addFlashAttribute("friendNotice", requestedFriend.nickname() + " 님에게 친구 요청을 보냈습니다.");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("friendError", switch (exception.getReason()) {
                case "friendCode is required" -> "친구 코드를 입력해주세요.";
                case "Friend code not found" -> "일치하는 친구 코드를 찾지 못했습니다.";
                case "You cannot add yourself" -> "내 친구 코드는 직접 추가할 수 없습니다.";
                case "Friend already added" -> "이미 추가된 친구입니다.";
                case "Friend request already sent" -> "이미 친구 요청을 보냈습니다.";
                case "Friend request already received" -> "상대가 이미 보낸 친구 요청이 있습니다. 받은 요청에서 승인해주세요.";
                default -> exception.getReason();
            });
        }
        return "redirect:" + redirectTo;
    }

    @PostMapping("/friends/favorite")
    public String updateFriendFavorite(
            @RequestParam String friendId,
            @RequestParam(defaultValue = "false") boolean favorite,
            @RequestParam(defaultValue = "") String keyword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        viewService.updateFriendFavorite(currentUser.getId(), friendId, favorite);
        if (keyword != null && !keyword.isBlank()) {
            redirectAttributes.addAttribute("keyword", keyword.trim());
        }
        return "redirect:/friends";
    }

    @PostMapping("/friends/delete")
    public String deleteFriend(
            @RequestParam(defaultValue = "") String friendId,
            @RequestParam(defaultValue = "") String keyword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        try {
            viewService.deleteFriend(currentUser.getId(), friendId);
            redirectAttributes.addFlashAttribute("friendNotice", "친구를 삭제했습니다.");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("friendError", "친구를 삭제하지 못했습니다.");
        }
        if (keyword != null && !keyword.isBlank()) {
            redirectAttributes.addAttribute("keyword", keyword.trim());
        }
        return "redirect:/friends";
    }

    @PostMapping("/friends/request/respond")
    public String respondFriendRequest(
            @RequestParam(defaultValue = "") String requesterId,
            @RequestParam(defaultValue = "") String action,
            @RequestParam(defaultValue = "/friends") String redirectTo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AppUser currentUser = viewHelper.requireLoggedInUser(session);
        boolean approved = "approve".equalsIgnoreCase(action);
        try {
            UserDTO.UserResponse requester = viewService.respondFriendRequest(currentUser.getId(), requesterId, approved);
            redirectAttributes.addFlashAttribute(
                    "friendRequestNotice",
                    approved ? requester.nickname() + " 님과 친구가 되었습니다." : requester.nickname() + " 님의 친구 요청을 거절했습니다."
            );
            redirectAttributes.addFlashAttribute("friendRequestNoticeTone", approved ? "success" : "error");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("friendRequestNotice", "친구 요청을 처리하지 못했습니다.");
            redirectAttributes.addFlashAttribute("friendRequestNoticeTone", "error");
        }
        return "redirect:" + redirectTo;
    }

    @ResponseBody
    @GetMapping("/api/friends")
    public List<UserDTO.UserResponse> apiFriends(@RequestHeader("Authorization") String authorization) {
        AppUser requester = authService.requireUser(authorization);
        return friendService.listFriends(requester.getId()).stream().map(authService::toUserResponse).toList();
    }

    @ResponseBody
    @PostMapping("/api/friends")
    public UserDTO.UserResponse apiAddFriend(
            @RequestHeader("Authorization") String authorization,
            @RequestBody FriendDTO.FriendAddRequest request
    ) {
        AppUser requester = authService.requireUser(authorization);
        return authService.toUserResponse(friendService.addFriendByCode(requester.getId(), request.friendCode()));
    }

    @ResponseBody
    @DeleteMapping("/api/friends")
    public void apiDeleteFriend(
            @RequestHeader("Authorization") String authorization,
            @RequestBody FriendDTO.FriendDeleteRequest request
    ) {
        AppUser requester = authService.requireUser(authorization);
        friendService.deleteFriend(requester.getId(), request.friendId());
    }

    private void populateFriendsModel(
            Model model,
            String userId,
            UserDTO.UserProfile profile,
            List<FriendDTO.FriendSummary> friends,
            String keyword,
            int page
    ) {
        int totalCount = friends.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / FRIEND_PAGE_SIZE));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = Math.min((currentPage - 1) * FRIEND_PAGE_SIZE, totalCount);
        int toIndex = Math.min(fromIndex + FRIEND_PAGE_SIZE, totalCount);
        List<FriendDTO.FriendSummary> pageFriends = friends.subList(fromIndex, toIndex);

        viewHelper.populateCommon(model, "friends", false);
        model.addAttribute("profile", profile);
        if (!model.containsAttribute("friendRequests")) {
            model.addAttribute("friendRequests", viewService.getFriendRequests(userId));
        }
        if (!model.containsAttribute("sentFriendRequests")) {
            model.addAttribute("sentFriendRequests", viewService.getSentFriendRequests(userId));
        }
        model.addAttribute("friends", pageFriends);
        model.addAttribute("friendTotalCount", totalCount);
        model.addAttribute("friendPage", currentPage);
        model.addAttribute("friendTotalPages", totalPages);
        model.addAttribute("friendHasPrevious", currentPage > 1);
        model.addAttribute("friendHasNext", currentPage < totalPages);
        model.addAttribute("friendPages", IntStream.rangeClosed(1, totalPages).boxed().toList());
        model.addAttribute("friendKeyword", keyword == null ? "" : keyword);
    }
}
