package com.kopo.wemeet.controller;

import com.kopo.wemeet.dto.MeetingDTO;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IMeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class MeetingApiController {
    // 모임 REST API를 분리한 컨트롤러다.

    private final IApiAuthService authService;
    private final IMeetingService meetingService;

    @ResponseBody
    @GetMapping("/api/meetings")
    public List<MeetingDTO.MeetingResponse> apiMeetings(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @CookieValue(name = "${app.auth.jwt.access-cookie-name:WM_ACCESS_TOKEN}", required = false) String accessToken
    ) {
        AppUser requester = authService.requireUser(authorization, accessToken);
        return meetingService.getApiMeetings(requester.getId());
    }

    @ResponseBody
    @PostMapping("/api/meetings")
    public MeetingDTO.MeetingResponse apiCreateMeeting(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @CookieValue(name = "${app.auth.jwt.access-cookie-name:WM_ACCESS_TOKEN}", required = false) String accessToken,
            @RequestBody MeetingDTO.MeetingCreateRequest request
    ) {
        AppUser requester = authService.requireUser(authorization, accessToken);
        return meetingService.createApiMeeting(requester.getId(), request);
    }
}
