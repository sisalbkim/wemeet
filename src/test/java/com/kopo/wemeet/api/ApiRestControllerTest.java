package com.kopo.wemeet.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginReturnsTokenAndMeEndpointWorks() throws Exception {
        String token = loginAndGetToken("user123", "pass1234");

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("user123"))
                .andExpect(jsonPath("$.nickname").value("김철수"));
    }

    @Test
    void recommendationsEndpointReturnsBalancedVenueList() throws Exception {
        String token = loginAndGetToken("user123", "pass1234");
        String payload = objectMapper.writeValueAsString(Map.of(
                "category", "맛집",
                "participantIds", List.of("friend-lee", "friend-park")
        ));

        mockMvc.perform(post("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("맛집"))
                .andExpect(jsonPath("$.venues[0].name").exists())
                .andExpect(jsonPath("$.midpoint.station").exists());
    }

    @Test
    void signupAndMeetingCreationWork() throws Exception {
        String signUpPayload = objectMapper.writeValueAsString(Map.of(
                "nickname", "새유저",
                "loginId", "newuser01",
                "password", "pass1234",
                "email", "newuser01@wemeet.local",
                "baseAddress", "서울특별시 영등포구 여의도동"
        ));

        MvcResult signUpResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.loginId").value("newuser01"))
                .andReturn();

        String token = objectMapper.readTree(signUpResult.getResponse().getContentAsString()).get("token").asText();

        String meetingPayload = objectMapper.writeValueAsString(Map.of(
                "title", "첫 모임",
                "description", "API로 생성한 모임",
                "meetingDate", "2026-04-01",
                "category", "카페",
                "participantIds", List.of()
        ));

        mockMvc.perform(post("/api/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingPayload))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("첫 모임")))
                .andExpect(jsonPath("$.category").value("카페"));
    }

    @Test
    void passwordResetFlowWorks() throws Exception {
        String resetRequestPayload = objectMapper.writeValueAsString(Map.of(
                "email", "user123@wemeet.local"
        ));

        MvcResult resetRequestResult = mockMvc.perform(post("/api/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetRequestPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetTokenPreview").exists())
                .andReturn();

        String resetToken = objectMapper.readTree(resetRequestResult.getResponse().getContentAsString())
                .get("resetTokenPreview")
                .asText();

        String confirmPayload = objectMapper.writeValueAsString(Map.of(
                "token", resetToken,
                "newPassword", "changedPass123!"
        ));

        mockMvc.perform(post("/api/auth/password/reset-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다."));

        String loginPayload = objectMapper.writeValueAsString(Map.of(
                "loginId", "user123",
                "password", "changedPass123!"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    private String loginAndGetToken(String loginId, String password) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "loginId", loginId,
                "password", password
        ));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
