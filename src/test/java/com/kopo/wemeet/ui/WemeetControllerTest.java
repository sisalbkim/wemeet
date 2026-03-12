package com.kopo.wemeet.ui;

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
}
