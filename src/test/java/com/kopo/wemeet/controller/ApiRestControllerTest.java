package com.kopo.wemeet.controller;

import com.kopo.wemeet.dto.ApiDtos;
import com.kopo.wemeet.service.impl.NaverPlaceSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NaverPlaceSearchService naverPlaceSearchService;

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
        given(naverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "중구 로컬 맛집"));
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
                .andExpect(jsonPath("$.venues[0].name").value("중구 로컬 맛집"))
                .andExpect(jsonPath("$.midpoint.station").exists());
    }

    @Test
    void placeTagsEndpointReturnsMobileFriendlyTagCatalog() throws Exception {
        mockMvc.perform(get("/api/place-tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].label").value("카페"))
                .andExpect(jsonPath("$.tags[0].queryTerms[0]").value("카페"));
    }

    @Test
    void placeSearchEndpointReturnsNaverCandidatesSortedForMapRendering() throws Exception {
        String token = loginAndGetToken("user123", "pass1234");
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

        String payload = objectMapper.writeValueAsString(Map.of(
                "originQuery", "성수역",
                "tag", "카페",
                "display", 5
        ));

        mockMvc.perform(post("/api/places/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combinedQuery").value("성수역 카페"))
                .andExpect(jsonPath("$.origin.name").value("성수역"))
                .andExpect(jsonPath("$.places[0].name").value("어니언 성수"))
                .andExpect(jsonPath("$.places[0].distanceMeters").value(410))
                .andExpect(jsonPath("$.places[0].routePath[0].latitude").value(37.5446));
    }

    @Test
    void publicPlaceSearchEndpointWorksWithoutAuthentication() throws Exception {
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
                                List.of()
                        )
                )
        ));

        String payload = objectMapper.writeValueAsString(Map.of(
                "originQuery", "성수역",
                "tag", "카페",
                "display", 5
        ));

        mockMvc.perform(post("/api/public/places/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalizedTag").value("카페"))
                .andExpect(jsonPath("$.places[0].name").value("어니언 성수"));
    }

    @Test
    void publicRecommendationsEndpointWorksWithoutAuthentication() throws Exception {
        given(naverPlaceSearchService.search(any())).willReturn(new ApiDtos.PlaceSearchResponse(
                "가산로5길54-1 카페",
                "카페",
                "카페",
                List.of("카페", "디저트"),
                List.of("카페,디저트"),
                new ApiDtos.PlaceSearchOriginResponse("가산로5길54-1", "가산로5길54-1", "서울 금천구 가산로5길 54-1", 37.4765, 126.8876),
                List.of(
                        new ApiDtos.PlaceCandidateResponse(
                                "가산 로컬 카페",
                                "카페",
                                "카페,디저트",
                                "서울 금천구 가산로5길 60",
                                "서울 금천구 가산로5길 60",
                                "",
                                "",
                                37.4767,
                                126.8880,
                                180,
                                2,
                                List.of()
                        )
                )
        ));

        String payload = objectMapper.writeValueAsString(Map.of(
                "baseAddress", "서울 금천구 가산로5길 54-1",
                "category", "카페",
                "mode", "CENTER"
        ));

        mockMvc.perform(post("/api/public/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("카페"))
                .andExpect(jsonPath("$.midpoint.station").value("가산로5길54-1"))
                .andExpect(jsonPath("$.venues[0].name").value("가산 로컬 카페"))
                .andExpect(jsonPath("$.venues[0].travelTimes[0].minutes").value(2))
                .andExpect(jsonPath("$.participants[0].nickname").value("게스트"));
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

    @Test
    void meAddressUpdateEndpointUpdatesBaseAddress() throws Exception {
        String token = loginAndGetToken("user123", "pass1234");
        String payload = objectMapper.writeValueAsString(Map.of(
                "baseAddress", "서울특별시 송파구 올림픽로 300"
        ));

        mockMvc.perform(post("/api/me/address")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseAddress").value("서울특별시 송파구 올림픽로 300"));

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseAddress").value("서울특별시 송파구 올림픽로 300"));
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

    private ApiDtos.PlaceSearchResponse samplePlaceSearch(String originQuery, String address, String placeName) {
        return new ApiDtos.PlaceSearchResponse(
                originQuery + " 맛집",
                "맛집",
                "맛집",
                List.of("맛집", "한식"),
                List.of("한식"),
                new ApiDtos.PlaceSearchOriginResponse(originQuery, originQuery, address, 37.5665, 126.9780),
                List.of(
                        new ApiDtos.PlaceCandidateResponse(
                                placeName,
                                "맛집",
                                "한식",
                                address,
                                address,
                                "",
                                "",
                                37.567,
                                126.979,
                                500,
                                5,
                                List.of()
                        )
                )
        );
    }
}
