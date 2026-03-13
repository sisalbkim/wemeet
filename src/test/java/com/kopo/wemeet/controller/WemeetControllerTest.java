package com.kopo.wemeet.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
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
                .andExpect(content().string(containsString("아이디")));
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
                .andExpect(redirectedUrl("/home"))
                .andReturn();

        mockMvc.perform(get("/home").session((org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("안녕하세요")))
                .andExpect(content().string(containsString("홈테스터")));
    }

    @Test
    void profilePageAllowsUpdatingBaseAddress() throws Exception {
        mockMvc.perform(post("/signup")
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
                .andExpect(redirectedUrl("/home"))
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
    void historyPageShowsUserSpecificHistoryInsteadOfStaticSample() throws Exception {
        mockMvc.perform(post("/signup")
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
                .andExpect(redirectedUrl("/home"))
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
                .andExpect(redirectedUrl("/home"))
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
}
