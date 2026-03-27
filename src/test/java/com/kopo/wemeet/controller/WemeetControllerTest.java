package com.kopo.wemeet.controller;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.service.impl.NaverPlaceSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WemeetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NaverPlaceSearchService naverPlaceSearchService;

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
    void passwordResetFlowRequiresMatchingLoginIdAndEmail() throws Exception {
        String signUpPayload = objectMapper.writeValueAsString(Map.of(
                "nickname", "비번테스터",
                "loginId", "passwordflow01",
                "password", "pass1234",
                "email", "passwordflow01@wemeet.local",
                "baseAddress", "서울특별시 중구"
        ));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpPayload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/find-password/verify")
                        .param("loginId", "passwordflow01")
                        .param("email", "wrong@wemeet.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/find-password"));

        MvcResult verifyResult = mockMvc.perform(post("/find-password/verify")
                        .session(new MockHttpSession())
                        .param("loginId", "passwordflow01")
                        .param("email", "passwordflow01@wemeet.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/find-password/reset"))
                .andReturn();

        MockHttpSession resetSession = (MockHttpSession) verifyResult.getRequest().getSession(false);

        mockMvc.perform(get("/find-password/reset")
                        .session(resetSession)
                        .flashAttrs(verifyResult.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("비밀번호 변경")))
                .andExpect(content().string(containsString("passwordflow01")))
                .andExpect(content().string(containsString("passwordflow01@wemeet.local")))
                .andExpect(content().string(containsString("계정이 확인되었습니다. 새 비밀번호를 입력해주세요.")));

        MvcResult confirmResult = mockMvc.perform(post("/find-password/reset")
                        .session(resetSession)
                        .param("newPassword", "resetPass123!")
                        .param("confirmPassword", "resetPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andReturn();

        mockMvc.perform(get("/login").flashAttrs(confirmResult.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("비밀번호가 변경되었습니다.")));

        mockMvc.perform(post("/login")
                        .param("loginId", "passwordflow01")
                        .param("password", "resetPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void guestPlanPageAllowsEnteringDepartureAddress() throws Exception {
        mockMvc.perform(get("/guest/plan"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("어디서 출발하시나요?")))
                .andExpect(content().string(containsString("출발지 주소")))
                .andExpect(content().string(containsString("네이버로 검색")));
    }

    @Test
    void guestRecommendationUsesProvidedAddressInsteadOfDefaultGuestAddress() throws Exception {
        given(naverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("용두동", "용두동", "용두 로컬 카페"));

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
        given(naverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("성수동", "성수동", "성수 로컬 맛집"));

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
        given(naverPlaceSearchService.search(any())).willReturn(new ApiDtos.PlaceSearchResponse(
                "성수역 카페",
                "카페",
                "카페",
                List.of("카페", "디저트", "베이커리"),
                List.of("카페,디저트"),
                new ApiDtos.PlaceSearchOriginResponse("성수역", "성수역", "서울 성동구 성수동2가", 37.5446, 127.0557),
                List.of(
                        new ApiDtos.PlaceCandidateResponse(
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
                                        new ApiDtos.PlaceRoutePointResponse(37.5446, 127.0557),
                                        new ApiDtos.PlaceRoutePointResponse(37.5448, 127.0561)
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
                        .param("loginId", "tester01")
                        .param("email", "tester01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 강남구"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));
    }

    @Test
    void homePageShowsLoggedInGreeting() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("homeuser01@wemeet.local"))
                        .param("nickname", "홈테스터")
                        .param("loginId", "homeuser01")
                        .param("email", "homeuser01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("loginId", "homeuser01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        mockMvc.perform(get("/").session((org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("안녕하세요")))
                .andExpect(content().string(containsString("홈테스터")));
    }

    @Test
    void profilePageAllowsUpdatingBaseAddress() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("profileuser01@wemeet.local"))
                        .param("nickname", "프로필테스터")
                        .param("loginId", "profileuser01")
                        .param("email", "profileuser01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "ww"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("loginId", "profileuser01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        org.springframework.mock.web.MockHttpSession session =
                (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/profile/address")
                        .session(session)
                        .param("baseAddress", "서울특별시 강남구 테헤란로 123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        mockMvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("서울특별시 강남구 테헤란로 123")))
                .andExpect(content().string(containsString("기본 출발지 주소가 수정되었습니다.")));
    }

    @Test
    void logoutInvalidatesSessionAndRedirectsToLanding() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("logoutuser01@wemeet.local"))
                        .param("nickname", "로그아웃테스터")
                        .param("loginId", "logoutuser01")
                        .param("email", "logoutuser01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("loginId", "logoutuser01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        org.springframework.mock.web.MockHttpSession session =
                (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/logout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/profile").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void historyPageShowsUserSpecificHistoryInsteadOfStaticSample() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("historyuser01@wemeet.local"))
                        .param("nickname", "히스토리테스터")
                        .param("loginId", "historyuser01")
                        .param("email", "historyuser01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("loginId", "historyuser01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        org.springframework.mock.web.MockHttpSession session =
                (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/history").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 검색 기록이 없습니다.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("강남 맛집"))));
    }

    @Test
    void friendCodeCanBeAddedFromFriendsPage() throws Exception {
        mockMvc.perform(post("/signup")
                        .session(verifiedSignupSession("friendadd01@wemeet.local"))
                        .param("nickname", "친구추가테스터")
                        .param("loginId", "friendadd01")
                        .param("email", "friendadd01@wemeet.local")
                        .param("password", "pass1234")
                        .param("confirmPassword", "pass1234")
                        .param("baseAddress", "서울특별시 중구"))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("loginId", "friendadd01")
                        .param("password", "pass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        org.springframework.mock.web.MockHttpSession session =
                (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/friends/add")
                        .session(session)
                        .param("friendCode", "FRIEND456")
                        .param("redirectTo", "/friends"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/friends"));

        mockMvc.perform(get("/friends").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이영희")))
                .andExpect(content().string(containsString("님을 친구로 추가했습니다.")));
    }

    private MockHttpSession verifiedSignupSession(String email) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SIGNUP_VERIFIED_EMAIL", email);
        return session;
    }

    private ApiDtos.PlaceSearchResponse samplePlaceSearch(String originQuery, String address, String placeName) {
        return new ApiDtos.PlaceSearchResponse(
                originQuery + " 카페",
                "카페",
                "카페",
                List.of("카페"),
                List.of("카페,디저트"),
                new ApiDtos.PlaceSearchOriginResponse(originQuery, originQuery, address, 37.575, 127.04),
                List.of(
                        new ApiDtos.PlaceCandidateResponse(
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
