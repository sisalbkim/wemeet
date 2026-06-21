package com.kopo.wemeet.controller;

import com.kopo.wemeet.dto.*;

import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.FriendRelationRepository;
import jakarta.servlet.http.Cookie;
import com.kopo.wemeet.service.IFriendService;
import com.kopo.wemeet.service.IMeetingService;
import com.kopo.wemeet.service.impl.ApiNaverPlaceSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WemeetControllerTest {
    // 서버 렌더링 화면과 폼 제출 흐름을 검증하는 통합 테스트다.

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private FriendRelationRepository friendRelationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IMeetingService meetingService;

    @Autowired
    private IFriendService friendService;

    @MockitoBean
    private ApiNaverPlaceSearchService apiNaverPlaceSearchService;

    @BeforeEach
    void setUpSeedData() {
        TestSeedData.ensure(userRepository, friendRelationRepository, passwordEncoder);
    }

    @Test
    void landingPageShowsGuestNavigation() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("근처 장소 검색")))
                .andExpect(content().string(containsString("로그인")));
    }

    @Test
    void loginPageShowsLoginForm() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("로그인")))
                .andExpect(content().string(containsString("아이디")))
                .andExpect(content().string(containsString("자동로그인")))
                .andExpect(content().string(containsString("/find-password")));
    }

    @Test
    void findPasswordPageShowsResetForms() throws Exception {
        mockMvc.perform(get("/find-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("비밀번호 찾기")))
                .andExpect(content().string(containsString("아이디")))
                .andExpect(content().string(containsString("이메일")))
                .andExpect(content().string(containsString("비밀번호 찾기")));
    }

    @Test
    void passwordResetFlowEmailsTemporaryPasswordWhenLoginIdAndEmailMatch() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("passwordflow01@wemeet.local"))
                        .param("nickname", "비번테스터")
                        .param("userId", "passwordflow01")
                        .param("email", "passwordflow01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/find-password/verify")
                        .param("userId", "passwordflow01")
                        .param("email", "wrong@wemeet.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/find-password"));

        MvcResult verifyResult = mockMvc.perform(post("/find-password/verify")
                        .param("userId", "passwordflow01")
                        .param("email", "passwordflow01@wemeet.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andReturn();

        String temporaryPassword = (String) verifyResult.getFlashMap().get("passwordResetPreview");

        mockMvc.perform(get("/login").flashAttrs(verifyResult.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("임시 비밀번호를 이메일로 전송했습니다.")));

        mockMvc.perform(post("/login")
                        .param("userId", "passwordflow01")
                        .param("password", temporaryPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void guestPlanPageAllowsEnteringDepartureAddress() throws Exception {
        mockMvc.perform(get("/guest/plan"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("어디서 출발하시나요?")))
                .andExpect(content().string(containsString("출발지 주소")))
                .andExpect(content().string(containsString("이동수단")))
                .andExpect(content().string(containsString("name=\"routeMode\"")))
                .andExpect(content().string(not(containsString("value=\"transit\""))))
                .andExpect(content().string(containsString("네이버 기반 추천 보기")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("네이버로 검색"))));
    }

    @Test
    void guestRecommendationUsesProvidedAddressInsteadOfDefaultGuestAddress() throws Exception {
        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("용두동", "용두동", "용두 로컬 카페"));

        mockMvc.perform(get("/search/results")
                        .param("guest", "true")
                        .param("category", "맛집")
                        .param("mode", "CENTER")
                        .param("guestAddress", "용두동"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("용두동")))
                .andExpect(content().string(containsString("용두 로컬 카페")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("서울특별시 중구 명동길 74"))));
    }

    @Test
    void recommendationPageShowsNaverMapPlaceholderWhenClientIdIsMissing() throws Exception {
        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("성수동", "성수동", "성수 로컬 맛집"));

        mockMvc.perform(get("/search/results")
                        .param("guest", "true")
                        .param("category", "맛집")
                        .param("mode", "CENTER")
                        .param("guestAddress", "성수동"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("네이버 지도 Client ID가 아직 설정되지 않았습니다.")))
                .andExpect(content().string(containsString("NAVER_MAPS_JS_CLIENT_ID")));
    }

    @Test
    void guestPlaceSearchPageRendersNaverResults() throws Exception {
        given(apiNaverPlaceSearchService.search(any())).willReturn(new PlaceDTO.PlaceSearchResponse(
                "성수역 카페",
                "카페",
                "카페",
                List.of("카페", "디저트", "베이커리"),
                List.of("카페,디저트"),
                new PlaceDTO.PlaceSearchOriginResponse("성수역", "성수역", "서울 성동구 성수동2가", 37.5446, 127.0557),
                List.of(
                        new PlaceDTO.PlaceCandidateResponse(
                                "어니언 성수",
                                "카페",
                                "카페,디저트",
                                "서울 성동구 성수동2가",
                                "서울 성동구 아차산로9길 8",
                                "",
                                "https://example.com/onion",
                                37.5448,
                                127.0561,
                                410,
                                3,
                                List.of(
                                        new PlaceDTO.PlaceRoutePointResponse(37.5446, 127.0557),
                                        new PlaceDTO.PlaceRoutePointResponse(37.5448, 127.0561)
                                )
                        )
                )
        ));

        mockMvc.perform(get("/guest/places")
                        .param("guestAddress", "성수역")
                        .param("category", "카페"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("성수역 카페")))
                .andExpect(content().string(containsString("어니언 성수")))
                .andExpect(content().string(containsString("디저트")))
                .andExpect(content().string(containsString("카페,디저트")))
                .andExpect(content().string(containsString("410m")));
    }

    @Test
    void signupPageShowsSignupForm() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("회원가입")))
                .andExpect(content().string(containsString("기본 출발지 주소")));
    }

    @Test
    void signupSubmitRedirectsToLoginWithNotice() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("tester01@wemeet.local"))
                        .param("nickname", "테스터")
                        .param("userId", "tester01")
                        .param("email", "tester01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 강남구"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("signupSuccess", true))
                .andExpect(flash().attribute("registeredNickname", "테스터"));
    }

    @Test
    void homePageShowsLoggedInGreeting() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("homeuser01@wemeet.local"))
                        .param("nickname", "홈테스터")
                        .param("userId", "homeuser01")
                        .param("email", "homeuser01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("userId", "homeuser01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        mockMvc.perform(get("/").cookie(loginResult.getResponse().getCookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("안녕하세요")))
                .andExpect(content().string(containsString("홈테스터")));
    }

    @Test
    void loginWithoutAutoLoginSetsSessionJwtCookie() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("rememberuser01@wemeet.local"))
                        .param("nickname", "리멤버테스터")
                        .param("userId", "rememberuser01")
                        .param("email", "rememberuser01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("userId", "rememberuser01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie accessTokenCookie = loginResult.getResponse().getCookie("WM_ACCESS_TOKEN");
        assertNotNull(accessTokenCookie);
        assertTrue(accessTokenCookie.isHttpOnly());
        assertEquals("/", accessTokenCookie.getPath());
        assertEquals(-1, accessTokenCookie.getMaxAge());
    }

    @Test
    void loginWithAutoLoginSetsPersistentJwtCookie() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("autologinuser01@wemeet.local"))
                        .param("nickname", "자동로그인테스터")
                        .param("userId", "autologinuser01")
                        .param("email", "autologinuser01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("userId", "autologinuser01")
                        .param("password", "pass1234")
                        .param("autoLogin", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie accessTokenCookie = loginResult.getResponse().getCookie("WM_ACCESS_TOKEN");
        Cookie refreshTokenCookie = loginResult.getResponse().getCookie("WM_REFRESH_TOKEN");
        assertNotNull(accessTokenCookie);
        assertNotNull(refreshTokenCookie);
        assertTrue(accessTokenCookie.getMaxAge() > 0);
        assertTrue(refreshTokenCookie.getMaxAge() > accessTokenCookie.getMaxAge());
    }

    @Test
    void accessCookieRestoresLoginWithoutExistingSession() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("rememberrestore01@wemeet.local"))
                        .param("nickname", "쿠키로그인테스터")
                        .param("userId", "rememberrestore01")
                        .param("email", "rememberrestore01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("userId", "rememberrestore01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie accessTokenCookie = loginResult.getResponse().getCookie("WM_ACCESS_TOKEN");
        assertNotNull(accessTokenCookie);

        mockMvc.perform(get("/").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("안녕하세요")))
                .andExpect(content().string(containsString("쿠키로그인테스터")));
    }

    @Test
    void homePageShowsActualUpcomingMeetingsForCurrentUser() throws Exception {
        Cookie[] session = signupAndLogin("homeagenda01", "homeagenda01@wemeet.local");
        String userId = userRepository.findByLoginId("homeagenda01").orElseThrow().getId();

        meetingService.createMeeting(
                userId,
                "홈에 보이는 실제 모임",
                "샘플이 아니라 저장된 모임이어야 합니다",
                LocalDate.now().plusDays(2),
                LocalTime.of(18, 0),
                "맛집",
                "홈 테스트 장소",
                "서울특별시 중구 홈로 2",
                List.of()
        );

        mockMvc.perform(get("/").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("홈에 보이는 실제 모임")))
                .andExpect(content().string(not(containsString("친구들과 보드게임"))));
    }

    @Test
    void profilePageAllowsUpdatingBaseAddress() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("profileuser01@wemeet.local"))
                        .param("nickname", "프로필테스터")
                        .param("userId", "profileuser01")
                        .param("email", "profileuser01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "ww"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("userId", "profileuser01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie[] session = loginResult.getResponse().getCookies();

        MvcResult addressResult = mockMvc.perform(post("/profile/address")
                        .cookie(session)
                        .param("baseAddress", "서울특별시 강남구 테헤란로 123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andReturn();

        mockMvc.perform(get("/profile")
                        .session((MockHttpSession) addressResult.getRequest().getSession(false))
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("서울특별시 강남구 테헤란로 123")))
                .andExpect(content().string(containsString("기본 출발지 주소가 수정되었습니다.")));
    }

    @Test
    void profilePageShowsMeetingsCreatedByCurrentUser() throws Exception {
        Cookie[] session = signupAndLogin("profilemeeting01", "profilemeeting01@wemeet.local");
        String userId = userRepository.findByLoginId("profilemeeting01").orElseThrow().getId();

        meetingService.createMeeting(
                userId,
                "프로필에서 보이는 모임",
                "내가 만든 모임 설명",
                LocalDate.of(2026, 5, 10),
                LocalTime.of(18, 30),
                "카페",
                "프로필 테스트 카페",
                "서울특별시 중구 테스트로 10",
                List.of()
        );

        mockMvc.perform(get("/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("내가 생성한 모임")))
                .andExpect(content().string(containsString("프로필에서 보이는 모임")))
                .andExpect(content().string(containsString("내가 만든 모임 설명")))
                .andExpect(content().string(containsString("프로필 테스트 카페")))
                .andExpect(content().string(containsString("네이버 지도에서 보기")))
                .andExpect(content().string(containsString("https://map.naver.com/p/search/%ED%94%84%EB%A1%9C%ED%95%84%20%ED%85%8C%EC%8A%A4%ED%8A%B8%20%EC%B9%B4%ED%8E%98")))
                .andExpect(content().string(containsString("서울특별시 중구 테스트로 10")))
                .andExpect(content().string(containsString("2026. 5. 10.")))
                .andExpect(content().string(containsString("참여자 1명")));
    }

    @Test
    void profilePageShowsMeetingsCurrentUserJoined() throws Exception {
        Cookie[] session = signupAndLogin("profilejoined01", "profilejoined01@wemeet.local");
        String userId = userRepository.findByLoginId("profilejoined01").orElseThrow().getId();
        String hostId = userRepository.findByLoginId("user456").orElseThrow().getId();

        String meetingId = meetingService.createMeeting(
                hostId,
                "친구가 만든 참여 모임",
                "내가 참여자로 들어간 모임 설명",
                LocalDate.of(2026, 5, 11),
                LocalTime.of(19, 15),
                "맛집",
                "참여 모임 테스트 식당",
                "서울특별시 종로구 참여로 11",
                List.of(userId)
        ).id();

        mockMvc.perform(get("/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("받은 모임 초대")))
                .andExpect(content().string(containsString("@이영희 님이 모임에 초대하셨습니다. 참여하시겠습니까?")))
                .andExpect(content().string(containsString("친구가 만든 참여 모임")))
                .andExpect(content().string(containsString("수락 대기중")))
                .andExpect(content().string(containsString("참여")))
                .andExpect(content().string(containsString("거절")));

        MvcResult invitationResult = mockMvc.perform(post("/profile/meetings/invitations/respond")
                        .cookie(session)
                        .param("meetingId", meetingId)
                        .param("action", "accept"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andReturn();

        mockMvc.perform(get("/profile")
                        .session((MockHttpSession) invitationResult.getRequest().getSession(false))
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("모임 초대를 수락했습니다.")))
                .andExpect(content().string(containsString("참여한 모임")))
                .andExpect(content().string(containsString("내가 참여자로 들어간 모임 설명")))
                .andExpect(content().string(containsString("참여 모임 테스트 식당")))
                .andExpect(content().string(containsString("네이버 지도에서 보기")))
                .andExpect(content().string(containsString("https://map.naver.com/p/search/%EC%B0%B8%EC%97%AC%20%EB%AA%A8%EC%9E%84%20%ED%85%8C%EC%8A%A4%ED%8A%B8%20%EC%8B%9D%EB%8B%B9")))
                .andExpect(content().string(containsString("서울특별시 종로구 참여로 11")))
                .andExpect(content().string(containsString("2026. 5. 11.")));
    }

    @Test
    void logoutInvalidatesSessionAndRedirectsToLanding() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("logoutuser01@wemeet.local"))
                        .param("nickname", "로그아웃테스터")
                        .param("userId", "logoutuser01")
                        .param("email", "logoutuser01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("userId", "logoutuser01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie[] session = loginResult.getResponse().getCookies();

        mockMvc.perform(get("/logout").cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void logoutClearsJwtCookies() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("logoutremember01@wemeet.local"))
                        .param("nickname", "로그아웃쿠키테스터")
                        .param("userId", "logoutremember01")
                        .param("email", "logoutremember01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("userId", "logoutremember01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie[] session = loginResult.getResponse().getCookies();
        Cookie accessCookie = loginResult.getResponse().getCookie("WM_ACCESS_TOKEN");
        Cookie refreshCookie = loginResult.getResponse().getCookie("WM_REFRESH_TOKEN");
        Cookie csrfCookie = loginResult.getResponse().getCookie("WM_CSRF_TOKEN");
        assertNotNull(accessCookie);
        assertNotNull(refreshCookie);
        assertNotNull(csrfCookie);

        MvcResult logoutResult = mockMvc.perform(get("/logout")
                        .cookie(session)
                        .cookie(accessCookie, refreshCookie, csrfCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie clearedAccessCookie = logoutResult.getResponse().getCookie("WM_ACCESS_TOKEN");
        Cookie clearedRefreshCookie = logoutResult.getResponse().getCookie("WM_REFRESH_TOKEN");
        Cookie clearedCsrfCookie = logoutResult.getResponse().getCookie("WM_CSRF_TOKEN");
        assertNotNull(clearedAccessCookie);
        assertNotNull(clearedRefreshCookie);
        assertNotNull(clearedCsrfCookie);
        assertEquals(0, clearedAccessCookie.getMaxAge());
        assertEquals(0, clearedRefreshCookie.getMaxAge());
        assertEquals(0, clearedCsrfCookie.getMaxAge());
    }

    @Test
    void historyPageShowsUserSpecificHistoryInsteadOfStaticSample() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("historyuser01@wemeet.local"))
                        .param("nickname", "히스토리테스터")
                        .param("userId", "historyuser01")
                        .param("email", "historyuser01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("userId", "historyuser01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie[] session = loginResult.getResponse().getCookies();

        mockMvc.perform(get("/history").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 검색 기록이 없습니다.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("강남 맛집"))));
    }

    @Test
    void friendCodeCanBeAddedFromFriendsPage() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("friendadd01@wemeet.local"))
                        .param("nickname", "친구추가테스터")
                        .param("userId", "friendadd01")
                        .param("email", "friendadd01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("userId", "friendadd01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie[] session = loginResult.getResponse().getCookies();

        MvcResult addFriendResult = mockMvc.perform(post("/friends/add")
                        .cookie(session)
                        .param("friendCode", "FRIEND456")
                        .param("redirectTo", "/friends"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/friends"))
                .andReturn();

        mockMvc.perform(get("/friends")
                        .session((MockHttpSession) addFriendResult.getRequest().getSession(false))
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("수락 대기중")))
                .andExpect(content().string(containsString("이영희")))
                .andExpect(content().string(containsString("님에게 친구 요청을 보냈습니다.")))
                .andExpect(content().string(not(containsString("data-friend-card"))));

        Cookie[] friendSession = login("user456", "pass1234");
        String requesterId = userRepository.findByLoginId("friendadd01").orElseThrow().getId();
        mockMvc.perform(post("/friends/request/respond")
                        .cookie(friendSession)
                        .param("requesterId", requesterId)
                        .param("action", "approve")
                        .param("redirectTo", "/friends"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/friends"));

        mockMvc.perform(get("/friends").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이영희")))
                .andExpect(content().string(containsString("data-friend-card")));
    }

    @Test
    void friendsPageDoesNotShowDemoFriendRequests() throws Exception {
        Cookie[] session = signupAndLogin("friendrequestempty01", "friendrequestempty01@wemeet.local");

        mockMvc.perform(get("/friends").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("최지우"))))
                .andExpect(content().string(not(containsString("받은 친구 요청"))));
    }

    @Test
    void friendsPageSearchesOnServer() throws Exception {
        Cookie[] session = signupAndLogin("friendsearch01", "friendsearch01@wemeet.local");
        addFriend(session, "friendsearch01", "FRIEND456");
        addFriend(session, "friendsearch01", "FRIEND789");
        mockMvc.perform(get("/friends").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-friend-card")))
                .andExpect(content().string(containsString("박민수")))
                .andExpect(content().string(containsString("이영희")))
                .andExpect(content().string(containsString("서울특별시 마포구 공덕동")))
                .andExpect(content().string(containsString("서울특별시 성동구 성수동1가")));

        mockMvc.perform(get("/friends").cookie(session).param("keyword", "박민수"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"박민수\"")))
                .andExpect(content().string(containsString("박민수")))
                .andExpect(content().string(not(containsString("이영희"))));
    }

    @Test
    void friendsPageShowsTenFriendsPerPage() throws Exception {
        Cookie[] session = signupAndLogin("friendpage01", "friendpage01@wemeet.local");
        for (String friendCode : List.of(
                "FRIEND456",
                "FRIEND789",
                "FRIENDKIM",
                "FRIENDYH",
                "FRIENDAREUM",
                "FRIENDMJ",
                "FRIENDHN",
                "FRIENDYS",
                "FRIENDDY",
                "FRIENDJS",
                "AAAAAA"
        )) {
            addFriend(session, "friendpage01", friendCode);
        }

        String firstPageHtml = mockMvc.perform(get("/friends").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("내 친구 (11)")))
                .andExpect(content().string(containsString("다음")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(firstPageHtml.split("data-friend-card", -1).length - 1 <= 10);

        mockMvc.perform(get("/friends").cookie(session).param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이전")));
    }

    @Test
    void favoriteFriendsArePinnedToTop() throws Exception {
        Cookie[] session = signupAndLogin("friendfavorite01", "friendfavorite01@wemeet.local");
        addFriend(session, "friendfavorite01", "FRIEND456");
        addFriend(session, "friendfavorite01", "FRIEND789");

        mockMvc.perform(post("/friends/favorite")
                        .cookie(session)
                        .param("friendId", "friend-lee")
                        .param("favorite", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/friends"));

        String html = mockMvc.perform(get("/friends").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("즐겨찾기")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(html.indexOf("이영희") < html.indexOf("박민수"));
    }

    @Test
    void registeredFriendCanBeDeletedFromFriendsPage() throws Exception {
        Cookie[] session = signupAndLogin("frienddelete01", "frienddelete01@wemeet.local");
        addFriend(session, "frienddelete01", "FRIEND456");
        String userId = userRepository.findByLoginId("frienddelete01").orElseThrow().getId();
        String friendId = userRepository.findByLoginId("user456").orElseThrow().getId();

        mockMvc.perform(get("/friends").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이영희")))
                .andExpect(content().string(containsString("/friends/delete")))
                .andExpect(content().string(containsString("삭제")));

        MvcResult deleteFriendResult = mockMvc.perform(post("/friends/delete")
                        .cookie(session)
                        .param("friendId", friendId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/friends"))
                .andReturn();

        mockMvc.perform(get("/friends")
                        .session((MockHttpSession) deleteFriendResult.getRequest().getSession(false))
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("친구를 삭제했습니다.")))
                .andExpect(content().string(not(containsString("이영희"))))
                .andExpect(content().string(containsString("아직 추가된 친구가 없습니다.")));

        assertTrue(friendService.listFriends(userId).stream().noneMatch(friend -> friend.id().equals(friendId)));
        assertTrue(friendService.listFriends(friendId).stream().noneMatch(friend -> friend.id().equals(userId)));
    }

    @Test
    void meetingFormProvidesClientSideFriendSearch() throws Exception {
        Cookie[] session = signupAndLogin("meetingfriendsearch01", "meetingfriendsearch01@wemeet.local");
        addFriend(session, "meetingfriendsearch01", "FRIEND456");
        addFriend(session, "meetingfriendsearch01", "FRIEND789");
        mockMvc.perform(post("/friends/favorite")
                        .cookie(session)
                        .param("friendId", "friend-lee")
                        .param("favorite", "true"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/meetings/new").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-friend-filter")))
                .andExpect(content().string(containsString("data-friend-card")))
                .andExpect(content().string(containsString("data-friend-search-submit")))
                .andExpect(content().string(containsString("data-friend-modal-open")))
                .andExpect(content().string(containsString("data-friend-modal")))
                .andExpect(content().string(containsString("data-friend-modal-checkbox")))
                .andExpect(content().string(containsString("data-participant-friend-checkbox")))
                .andExpect(content().string(containsString("전체 친구 보기")))
                .andExpect(content().string(not(containsString("친구 관리로 이동"))))
                .andExpect(content().string(containsString("name=\"meetingHour\"")))
                .andExpect(content().string(containsString("name=\"meetingMinute\"")))
                .andExpect(content().string(containsString("이동수단")))
                .andExpect(content().string(containsString("name=\"routeMode\"")))
                .andExpect(content().string(not(containsString("value=\"transit\""))))
                .andExpect(content().string(containsString("박민수")))
                .andExpect(content().string(containsString("이영희")))
                .andExpect(content().string(containsString("서울특별시 마포구 공덕동")))
                .andExpect(content().string(containsString("서울특별시 성동구 성수동1가")));
    }

    @Test
    void meetingPreviewComposesTimeFromHourAndMinute() throws Exception {
        Cookie[] session = signupAndLogin("meetingtime01", "meetingtime01@wemeet.local");

        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "중구 로컬 카페"));

        mockMvc.perform(post("/meetings/preview")
                        .cookie(session)
                        .param("category", "카페")
                        .param("friendIds", "user-123")
                        .param("mode", "CENTER")
                        .param("meetingName", "저녁 모임")
                        .param("meetingDate", "2026-04-20")
                        .param("meetingHour", "19")
                        .param("meetingMinute", "30")
                        .param("routeMode", "transit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("중구 로컬 카페")))
                .andExpect(content().string(containsString("모임 저장")))
                .andExpect(content().string(containsString("name=\"meetingPreviewKey\"")))
                .andExpect(content().string(containsString("data-route-mode=\"car\"")))
                .andExpect(content().string(not(containsString("data-route-mode=\"transit\""))))
                .andExpect(content().string(containsString("data-route-mode=\"walk\"")));
    }

    @Test
    void meetingPreviewUsesTemporaryOriginAddressWhenProfileAddressIsMissing() throws Exception {
        Cookie[] session = signupAndLogin("meetingorigin01", "meetingorigin01@wemeet.local");
        clearBaseAddress("meetingorigin01");

        mockMvc.perform(get("/meetings/new").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"originAddress\"")))
                .andExpect(content().string(containsString("이번 추천에 사용할 출발지")));

        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 성동구", "서울특별시 성동구", "성수 로컬 카페"));

        mockMvc.perform(post("/meetings/preview")
                        .cookie(session)
                        .param("category", "카페")
                        .param("mode", "CENTER")
                        .param("originAddress", "서울특별시 성동구 성수동1가"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("성수 로컬 카페")))
                .andExpect(content().string(containsString("서울특별시 성동구 성수동1가")))
                .andExpect(content().string(containsString("name=\"originAddress\"")));
    }

    @Test
    void loggedInRecommendationPageDoesNotShowMeetingSavePanelWithoutPreview() throws Exception {
        Cookie[] session = signupAndLogin("resultsave01", "resultsave01@wemeet.local");
        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "중구 로컬 카페"));

        mockMvc.perform(get("/search/results")
                        .cookie(session)
                        .param("category", "카페"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("중구 로컬 카페")))
                .andExpect(content().string(not(containsString("모임 저장"))));
    }

    @Test
    void meetingCreateAddsMeetingToSelectedParticipants() throws Exception {
        Cookie[] session = signupAndLogin("meetingcreate01", "meetingcreate01@wemeet.local");
        addFriend(session, "meetingcreate01", "FRIEND456");
        String friendId = userRepository.findByLoginId("user456").orElseThrow().getId();

        mockMvc.perform(post("/meetings")
                        .cookie(session)
                        .param("meetingName", "참여자에게 추가되는 모임")
                        .param("meetingDescription", "선택 인원과 함께 저장됩니다")
                        .param("meetingDate", "2026-05-12")
                        .param("meetingTime", "18:30")
                        .param("meetingPlaceName", "명륜진사갈비 서울후암점")
                        .param("meetingPlaceAddress", "서울특별시 용산구 후암로 35-1 1층")
                        .param("category", "맛집")
                        .param("friendIds", friendId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        assertTrue(meetingService.listMeetingsForUser(friendId).stream()
                .anyMatch(meeting -> "참여자에게 추가되는 모임".equals(meeting.title())
                        && "명륜진사갈비 서울후암점".equals(meeting.meetingPlaceName())));
    }

    @Test
    void meetingCreateWithBlankRequiredFieldsStaysOnResultsPage() throws Exception {
        Cookie[] session = signupAndLogin("meetingerror01", "meetingerror01@wemeet.local");
        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "중구 로컬 카페"));

        MvcResult previewResult1 = mockMvc.perform(post("/meetings/preview")
                        .cookie(session)
                        .param("category", "카페")
                        .param("mode", "CENTER")
                        .param("meetingName", "")
                        .param("meetingDate", "")
                        .param("meetingHour", "18")
                        .param("meetingMinute", "30")
                        .param("routeMode", "walk"))
                .andExpect(status().isOk())
                .andReturn();

        String previewHtml = previewResult1.getResponse().getContentAsString();

        String previewKey = extractHiddenInputValue(previewHtml, "meetingPreviewKey");

        mockMvc.perform(post("/meetings")
                        .session((MockHttpSession) previewResult1.getRequest().getSession(false))
                        .cookie(session)
                        .param("meetingName", "")
                        .param("meetingDescription", "결과 페이지에 남아야 합니다")
                        .param("meetingDate", "")
                        .param("meetingTime", "18:30")
                        .param("meetingPlaceName", "중구 로컬 카페")
                        .param("meetingPlaceAddress", "서울특별시 중구 저장로 12")
                        .param("category", "카페")
                        .param("recommendationMode", "CENTER")
                        .param("routeMode", "walk")
                        .param("meetingPreviewKey", previewKey))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("모임 이름과 날짜를 입력해주세요.")))
                .andExpect(content().string(containsString("중구 로컬 카페")))
                .andExpect(content().string(containsString("모임 저장")));
    }

    @Test
    void meetingCreateStoresRecommendationSnapshotUntilSevenDaysAfterMeeting() throws Exception {
        Cookie[] session = signupAndLogin("meetingsnapshot01", "meetingsnapshot01@wemeet.local");
        String userId = userRepository.findByLoginId("meetingsnapshot01").orElseThrow().getId();

        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "저장된 추천 카페"));

        MvcResult previewResult2 = mockMvc.perform(post("/meetings/preview")
                        .cookie(session)
                        .param("category", "카페")
                        .param("mode", "CENTER")
                        .param("meetingName", "스냅샷 저장 모임")
                        .param("meetingDate", "2026-05-12")
                        .param("meetingHour", "18")
                        .param("meetingMinute", "30")
                        .param("routeMode", "car"))
                .andExpect(status().isOk())
                .andReturn();

        String previewHtml = previewResult2.getResponse().getContentAsString();

        String previewKey = extractHiddenInputValue(previewHtml, "meetingPreviewKey");
        assertFalse(previewKey.isBlank());

        mockMvc.perform(post("/meetings")
                        .session((MockHttpSession) previewResult2.getRequest().getSession(false))
                        .cookie(session)
                        .param("meetingName", "스냅샷 저장 모임")
                        .param("meetingDescription", "추천 결과를 같이 저장합니다")
                        .param("meetingDate", "2026-05-12")
                        .param("meetingTime", "18:30")
                        .param("meetingPlaceName", "저장된 추천 카페")
                        .param("meetingPlaceAddress", "서울특별시 중구 저장로 12")
                        .param("category", "카페")
                        .param("recommendationMode", "CENTER")
                        .param("anchorId", userId)
                        .param("meetingPreviewKey", previewKey))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        MeetingDTO.MeetingRecord savedMeeting = meetingService.listMeetingsCreatedByUser(userId).stream()
                .filter(meeting -> "스냅샷 저장 모임".equals(meeting.title()))
                .findFirst()
                .orElseThrow();

        assertFalse(savedMeeting.recommendationSnapshotJson().isBlank());
        assertEquals(LocalDate.of(2026, 5, 19), savedMeeting.recommendationSnapshotExpiresAt().toLocalDate());
    }

    @Test
    void profileCanDeleteCreatedMeeting() throws Exception {
        Cookie[] session = signupAndLogin("meetingdelete01", "meetingdelete01@wemeet.local");
        String userId = userRepository.findByLoginId("meetingdelete01").orElseThrow().getId();
        String meetingId = meetingService.createMeeting(
                userId,
                "삭제될 모임",
                "삭제 테스트",
                LocalDate.of(2026, 6, 1),
                LocalTime.of(20, 0),
                "문화",
                "삭제 테스트 장소",
                "서울특별시 중구 삭제로 1",
                List.of()
        ).id();

        MvcResult deleteMeetingResult = mockMvc.perform(post("/profile/meetings/delete")
                        .cookie(session)
                        .param("meetingId", meetingId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andReturn();

        mockMvc.perform(get("/profile")
                        .session((MockHttpSession) deleteMeetingResult.getRequest().getSession(false))
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("모임이 삭제되었습니다.")))
                .andExpect(content().string(not(containsString("삭제될 모임"))));
    }

    @Test
    void profileDeleteConfirmPageShowsConfirmationButtons() throws Exception {
        Cookie[] session = signupAndLogin("profiledeleteview01", "profiledeleteview01@wemeet.local");
        MockHttpSession editSession = new MockHttpSession();
        editSession.setAttribute("PROFILE_EDIT_VERIFIED", true);

        mockMvc.perform(get("/profile/delete").session(editSession).cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("정말로 탈퇴하시겠습니까?")))
                .andExpect(content().string(containsString(">예<")))
                .andExpect(content().string(containsString(">아니오<")));
    }

    @Test
    void profileDeleteRemovesUserAndRelatedData() throws Exception {
        Cookie[] session = signupAndLogin("accountdelete01", "accountdelete01@wemeet.local");
        MockHttpSession editSession = new MockHttpSession();
        editSession.setAttribute("PROFILE_EDIT_VERIFIED", true);
        String deletedUserId = userRepository.findByLoginId("accountdelete01").orElseThrow().getId();
        String otherUserId = userRepository.findByLoginId("user456").orElseThrow().getId();

        addFriend(session, "accountdelete01", "FRIEND456");

        meetingService.createMeeting(
                deletedUserId,
                "탈퇴와 함께 지워질 모임",
                "호스트 탈퇴 시 삭제되어야 합니다",
                LocalDate.of(2026, 6, 15),
                LocalTime.of(19, 0),
                "카페",
                "삭제될 호스트 모임 장소",
                "서울특별시 중구 삭제호스트로 15",
                List.of(otherUserId)
        );

        String survivingMeetingId = meetingService.createMeeting(
                otherUserId,
                "호스트는 남는 모임",
                "참여자만 탈퇴합니다",
                LocalDate.of(2026, 6, 16),
                LocalTime.of(20, 0),
                "맛집",
                "남아야 할 장소",
                "서울특별시 종로구 유지로 16",
                List.of(deletedUserId)
        ).id();

        MvcResult deleteResult = mockMvc.perform(post("/profile/delete").session(editSession).cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andReturn();

        mockMvc.perform(get("/login").flashAttrs(deleteResult.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("회원탈퇴가 완료되었습니다.")));

        assertTrue(userRepository.findByLoginId("accountdelete01").isEmpty());
        assertTrue(meetingService.listMeetingsCreatedByUser(deletedUserId).isEmpty());
        assertTrue(friendService.listFriends(otherUserId).stream().noneMatch(friend -> friend.id().equals(deletedUserId)));
        assertFalse(meetingService.findMeetingCreatedByUser(otherUserId, survivingMeetingId).orElseThrow().participantIds().contains(deletedUserId));
    }

    @Test
    void profileMeetingDetailCanOpenRecommendationResults() throws Exception {
        Cookie[] session = signupAndLogin("meetingresults01", "meetingresults01@wemeet.local");
        addFriend(session, "meetingresults01", "FRIEND456");
        String userId = userRepository.findByLoginId("meetingresults01").orElseThrow().getId();
        String friendId = userRepository.findByLoginId("user456").orElseThrow().getId();
        String meetingId = meetingService.createMeeting(
                userId,
                "결과 다시 보기 모임",
                "추천 결과로 다시 이동",
                LocalDate.of(2026, 6, 8),
                LocalTime.of(19, 0),
                "카페",
                "결과 테스트 카페",
                "서울특별시 중구 결과로 1",
                List.of(friendId)
        ).id();

        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "중구 로컬 카페"));

        mockMvc.perform(get("/profile/meetings/results")
                        .cookie(session)
                        .param("meetingId", meetingId)
                        .param("routeMode", "walk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("중구 로컬 카페")))
                .andExpect(content().string(containsString("카테고리 바꾸기")))
                .andExpect(content().string(containsString("data-route-mode=\"car\"")))
                .andExpect(content().string(not(containsString("data-route-mode=\"transit\""))))
                .andExpect(content().string(containsString("data-route-mode=\"walk\"")))
                .andExpect(content().string(containsString("이영희")));
    }

    @Test
    void profileMeetingDetailUsesSavedRecommendationSnapshotWhenAvailable() throws Exception {
        Cookie[] session = signupAndLogin("savedresult01", "savedresult01@wemeet.local");
        String userId = userRepository.findByLoginId("savedresult01").orElseThrow().getId();

        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "저장된 결과 카페"));

        MvcResult previewResult3 = mockMvc.perform(post("/meetings/preview")
                        .cookie(session)
                        .param("category", "카페")
                        .param("mode", "CENTER")
                        .param("meetingName", "저장 결과 확인 모임")
                        .param("meetingDate", "2026-06-12")
                        .param("meetingHour", "19")
                        .param("meetingMinute", "00")
                        .param("routeMode", "walk"))
                .andExpect(status().isOk())
                .andReturn();

        String previewHtml = previewResult3.getResponse().getContentAsString();

        String previewKey = extractHiddenInputValue(previewHtml, "meetingPreviewKey");

        mockMvc.perform(post("/meetings")
                        .session((MockHttpSession) previewResult3.getRequest().getSession(false))
                        .cookie(session)
                        .param("meetingName", "저장 결과 확인 모임")
                        .param("meetingDescription", "저장본을 다시 본다")
                        .param("meetingDate", "2026-06-12")
                        .param("meetingTime", "19:00")
                        .param("meetingPlaceName", "저장된 결과 카페")
                        .param("meetingPlaceAddress", "서울특별시 중구 저장결과로 12")
                        .param("category", "카페")
                        .param("recommendationMode", "CENTER")
                        .param("anchorId", userId)
                        .param("meetingPreviewKey", previewKey))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        String meetingId = meetingService.listMeetingsCreatedByUser(userId).stream()
                .filter(meeting -> "저장 결과 확인 모임".equals(meeting.title()))
                .findFirst()
                .orElseThrow()
                .id();

        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "바뀐 최신 카페"));

        mockMvc.perform(get("/profile/meetings/results")
                        .cookie(session)
                        .param("meetingId", meetingId)
                        .param("routeMode", "walk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("저장된 결과 카페")))
                .andExpect(content().string(containsString("저장된 추천 결과를 보여주고 있습니다.")))
                .andExpect(content().string(not(containsString("바뀐 최신 카페"))));
    }

    @Test
    void profileMeetingNaverMapLinkUsesSavedRouteWhenSnapshotExists() throws Exception {
        Cookie[] session = signupAndLogin("savedroute01", "savedroute01@wemeet.local");
        String userId = userRepository.findByLoginId("savedroute01").orElseThrow().getId();

        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "저장된 결과 카페"));

        MvcResult previewResult4 = mockMvc.perform(post("/meetings/preview")
                        .cookie(session)
                        .param("category", "카페")
                        .param("mode", "CENTER")
                        .param("meetingName", "지도 링크 확인 모임")
                        .param("meetingDate", "2026-06-15")
                        .param("meetingHour", "19")
                        .param("meetingMinute", "30")
                        .param("routeMode", "car"))
                .andExpect(status().isOk())
                .andReturn();

        String previewHtml = previewResult4.getResponse().getContentAsString();

        String previewKey = extractHiddenInputValue(previewHtml, "meetingPreviewKey");

        mockMvc.perform(post("/meetings")
                        .session((MockHttpSession) previewResult4.getRequest().getSession(false))
                        .cookie(session)
                        .param("meetingName", "지도 링크 확인 모임")
                        .param("meetingDescription", "프로필 링크 확인")
                        .param("meetingDate", "2026-06-15")
                        .param("meetingTime", "19:30")
                        .param("meetingPlaceName", "저장된 결과 카페")
                        .param("meetingPlaceAddress", "서울특별시 중구 저장결과로 12")
                        .param("category", "카페")
                        .param("recommendationMode", "CENTER")
                        .param("anchorId", userId)
                        .param("meetingPreviewKey", previewKey))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        String profileHtml = mockMvc.perform(get("/profile").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String naverMapHref = extractAnchorHref(profileHtml, "네이버 지도에서 보기")
                .replace("&amp;", "&");
        String decodedHref = URLDecoder.decode(naverMapHref, StandardCharsets.UTF_8);

        assertTrue(decodedHref.contains("menu=route"));
        assertTrue(decodedHref.contains("stext=서울특별시 중구"));
        assertTrue(decodedHref.contains("etext=저장된 결과 카페"));
        assertTrue(decodedHref.contains("slat="));
        assertTrue(decodedHref.contains("elat="));
    }

    @Test
    void participantCanOpenRecommendationResultsFromProfile() throws Exception {
        Cookie[] session = signupAndLogin("participantresults01", "participantresults01@wemeet.local");
        String participantUserId = userRepository.findByLoginId("participantresults01").orElseThrow().getId();
        String hostUserId = userRepository.findByLoginId("user456").orElseThrow().getId();
        String meetingId = meetingService.createMeeting(
                hostUserId,
                "참여자 결과 보기 모임",
                "참여자도 결과를 열 수 있어야 함",
                LocalDate.of(2026, 6, 9),
                LocalTime.of(18, 45),
                "카페",
                "참여 결과 카페",
                "서울특별시 중구 참여결과로 9",
                List.of(participantUserId)
        ).id();

        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "중구 로컬 카페"));

        mockMvc.perform(get("/profile/meetings/results")
                        .cookie(session)
                        .param("meetingId", meetingId)
                        .param("routeMode", "car"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("중구 로컬 카페")))
                .andExpect(content().string(containsString("카테고리 바꾸기")))
                .andExpect(content().string(containsString("data-route-mode=\"car\"")));
    }

    private MockHttpSession verifiedSignupSession(String email) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SIGNUP_VERIFIED_EMAIL", email);
        return session;
    }

    private Cookie[] signupAndLogin(String loginId, String email) throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession(email))
                        .param("nickname", loginId + "닉네임")
                        .param("userId", loginId)
                        .param("email", email)
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        return login(loginId, "pass1234");
    }

    private void clearBaseAddress(String loginId) {
        var user = userRepository.findByLoginId(loginId).orElseThrow();
        user.changeBaseAddress("");
        userRepository.save(user);
    }

    private Cookie[] login(String loginId, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("userId", loginId)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        return loginResult.getResponse().getCookies();
    }

    private void addFriend(Cookie[] session, String requesterLoginId, String friendCode) throws Exception {
        mockMvc.perform(post("/friends/add")
                        .cookie(session)
                        .param("friendCode", friendCode)
                        .param("redirectTo", "/friends"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/friends"));

        Cookie[] recipientSession = login(loginIdForFriendCode(friendCode), passwordForFriendCode(friendCode));
        String requesterId = userRepository.findByLoginId(requesterLoginId).orElseThrow().getId();
        mockMvc.perform(post("/friends/request/respond")
                        .cookie(recipientSession)
                        .param("requesterId", requesterId)
                        .param("action", "approve")
                        .param("redirectTo", "/friends"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/friends"));
    }

    private String loginIdForFriendCode(String friendCode) {
        return switch (friendCode) {
            case "FRIEND456" -> "user456";
            case "FRIEND789" -> "user789";
            case "FRIENDKIM" -> "friendkim";
            case "FRIENDYH" -> "parkyounghee";
            case "FRIENDAREUM" -> "goareum";
            case "FRIENDMJ" -> "choiminjun";
            case "FRIENDHN" -> "junghana";
            case "FRIENDYS" -> "yoonseo";
            case "FRIENDDY" -> "kangdoyun";
            case "FRIENDJS" -> "hanjisoo";
            case "AAAAAA" -> "test_friend_aaaaaa";
            default -> throw new IllegalArgumentException("Unknown friend code: " + friendCode);
        };
    }

    private String passwordForFriendCode(String friendCode) {
        return "AAAAAA".equals(friendCode) ? "Passw0rd!" : "pass1234";
    }

    private String extractHiddenInputValue(String html, String inputName) {
        Pattern pattern = Pattern.compile("name=\"" + Pattern.quote(inputName) + "\"[^>]*value=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(html);
        assertTrue(matcher.find(), "Expected hidden input for " + inputName);
        return matcher.group(1);
    }

    private String extractAnchorHref(String html, String linkText) {
        Pattern pattern = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>\\s*" + Pattern.quote(linkText) + "\\s*</a>");
        Matcher matcher = pattern.matcher(html);
        assertTrue(matcher.find(), "Expected link for " + linkText);
        return matcher.group(1);
    }

    private PlaceDTO.PlaceSearchResponse samplePlaceSearch(String originQuery, String address, String placeName) {
        return new PlaceDTO.PlaceSearchResponse(
                originQuery + " 카페",
                "카페",
                "카페",
                List.of("카페"),
                List.of("카페,디저트"),
                new PlaceDTO.PlaceSearchOriginResponse(originQuery, originQuery, address, 37.575, 127.04),
                List.of(
                        new PlaceDTO.PlaceCandidateResponse(
                                placeName,
                                "카페",
                                "카페,디저트",
                                address,
                                address,
                                "",
                                "",
                                37.5755,
                                127.041,
                                320,
                                4,
                                List.of()
                        )
                )
        );
    }
}





