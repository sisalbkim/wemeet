package com.kopo.wemeet.controller;

import com.kopo.wemeet.dto.*;

import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.FriendRelationRepository;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.impl.ApiNaverPlaceSearchService;
import com.kopo.wemeet.service.impl.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiRestControllerTest {
    // REST API 인증, 친구, 모임 흐름을 검증하는 통합 테스트다.

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
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private ApiNaverPlaceSearchService apiNaverPlaceSearchService;

    @BeforeEach
    void setUpSeedData() {
        TestSeedData.ensure(userRepository, friendRelationRepository, passwordEncoder);
    }

    @Test
    void apiCorsAllowsCredentialsForConfiguredFrontendOrigins() throws Exception {
        mockMvc.perform(options("/api/meetings")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void accessCookieAuthenticatesProtectedApiGroups() throws Exception {
        Cookie accessCookie = loginAndGetAccessCookie("user123", "pass1234");

        mockMvc.perform(get("/api/friends").cookie(accessCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/meetings").cookie(accessCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/history").cookie(accessCookie))
                .andExpect(status().isOk());
    }

    @Test
    void refreshCookieRenewsAccessCookieForProtectedApiGroups() throws Exception {
        MvcResult loginResult = loginAndGetResult("user123", "pass1234");
        Cookie refreshCookie = loginResult.getResponse().getCookie("WM_REFRESH_TOKEN");
        assertNotNull(refreshCookie);

        MvcResult apiResult = mockMvc.perform(get("/api/meetings").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie renewedAccessCookie = apiResult.getResponse().getCookie("WM_ACCESS_TOKEN");
        assertNotNull(renewedAccessCookie);
    }

    @Test
    void recommendationsEndpointReturnsBalancedVenueList() throws Exception {
        String token = accessTokenFor("user123");
        given(apiNaverPlaceSearchService.search(any())).willReturn(samplePlaceSearch("서울특별시 중구", "서울특별시 중구", "중구 로컬 맛집"));
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
        String token = accessTokenFor("user123");
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
        given(apiNaverPlaceSearchService.search(any())).willReturn(new PlaceDTO.PlaceSearchResponse(
                "가산로5길54-1 카페",
                "카페",
                "카페",
                List.of("카페", "디저트"),
                List.of("카페,디저트"),
                new PlaceDTO.PlaceSearchOriginResponse("가산로5길54-1", "가산로5길54-1", "서울 금천구 가산로5길 54-1", 37.4765, 126.8876),
                List.of(
                        new PlaceDTO.PlaceCandidateResponse(
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
    void loginAndMeetingCreationWorkForExistingUser() throws Exception {
        if (!userRepository.existsByLoginId("newuser01")) {
            userRepository.save(new AppUser(
                    "api-newuser01",
                    "newuser01",
                    "새유저",
                    "newuser01@wemeet.local",
                    passwordEncoder.encode("pass1234"),
                    "NEWUSER01",
                    "서울특별시 영등포구 여의도동"
            ));
        }

        String token = accessTokenFor("newuser01");

        String meetingPayload = objectMapper.writeValueAsString(Map.of(
                "title", "첫 모임",
                "description", "API로 생성한 모임",
                "meetingDate", "2026-04-01",
                "meetingTime", "19:30",
                "category", "카페",
                "participantIds", List.of()
        ));

        mockMvc.perform(post("/api/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingPayload))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("첫 모임")))
                .andExpect(jsonPath("$.meetingDate").value("2026-04-01"))
                .andExpect(jsonPath("$.meetingTime").value("19:30"))
                .andExpect(jsonPath("$.category").value("카페"));
    }

    @Test
    void signupEmailVerificationSendCodeFallsBackToPreviewWhenMailIsDisabled() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "email", "preview-mail@wemeet.local"
        ));

        mockMvc.perform(post("/api/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.codePreview").isNotEmpty())
                .andExpect(jsonPath("$.message").value(containsString("화면용 인증코드")));
    }

    private String accessTokenFor(String loginId) {
        AppUser user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalStateException("Missing test user: " + loginId));
        return jwtTokenService.createAccessToken(user.getId());
    }

    private Cookie loginAndGetAccessCookie(String loginId, String password) throws Exception {
        Cookie cookie = loginAndGetResult(loginId, password).getResponse().getCookie("WM_ACCESS_TOKEN");
        assertNotNull(cookie);
        return cookie;
    }

    private MvcResult loginAndGetResult(String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("userId", loginId)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return result;
    }

    private PlaceDTO.PlaceSearchResponse samplePlaceSearch(String originQuery, String address, String placeName) {
        return new PlaceDTO.PlaceSearchResponse(
                originQuery + " 맛집",
                "맛집",
                "맛집",
                List.of("맛집", "한식"),
                List.of("한식"),
                new PlaceDTO.PlaceSearchOriginResponse(originQuery, originQuery, address, 37.5665, 126.9780),
                List.of(
                        new PlaceDTO.PlaceCandidateResponse(
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


