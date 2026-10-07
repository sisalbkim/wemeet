package com.kopo.wemeet.controller;

import com.kopo.wemeet.service.impl.*;
import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.*;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IApiRecommendationService;
import com.kopo.wemeet.service.IWemeetViewService;
import com.kopo.wemeet.service.impl.RecommendationMoreService;
import com.kopo.wemeet.service.impl.RecommendationSupport;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

import static com.kopo.wemeet.util.UiDefaults.DEFAULT_CATEGORY;
import static com.kopo.wemeet.util.UiDefaults.DEFAULT_GUEST_ADDRESS;
import static com.kopo.wemeet.util.UiDefaults.DEFAULT_PLACE_DISPLAY_COUNT;
import static com.kopo.wemeet.util.UiDefaults.DEFAULT_RECOMMENDATION_MODE;
import static com.kopo.wemeet.util.UiDefaults.DEFAULT_ROUTE_MODE;
import static com.kopo.wemeet.util.UiDefaults.TAB_GUEST_HOME;
import static com.kopo.wemeet.util.UiDefaults.TAB_HOME;
import static com.kopo.wemeet.util.UiDefaults.TAB_NEARBY;

/**
 * MeetController는 화면 요청과 API 요청을 받아 서비스 계층으로 위임하는 MVC 컨트롤러입니다.
 */
@Controller
@RequiredArgsConstructor
public class MeetController {
    // 홈, 게스트 추천, 장소 검색 같은 메인 화면 진입점을 연결하는 컨트롤러.

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final IApiRecommendationService recommendationService;
    private final ApiNaverPlaceSearchService apiNaverPlaceSearchService;
    private final NaverPlaceTagCatalog naverPlaceTagCatalog;
    private final WemeetViewHelper viewHelper;
    private final RecommendationMoreService recommendationMoreService;

    @GetMapping("/")
    public String landing(Model model, HttpSession session) {
        AppUser currentUser = viewHelper.findLoggedInUser(session);
        if (currentUser != null) {
            populateHomeModel(model, currentUser);
            return "home/index";
        }

        viewHelper.populateCommon(model, TAB_GUEST_HOME, true);
        model.addAttribute("categories", viewService.getSelectableCategories());
        return "landing/index";
    }

    @GetMapping("/home")
    public String home(Model model, HttpSession session) {
        viewHelper.requireLoggedInUser(session);
        return "redirect:/";
    }

    @GetMapping("/guest/plan")
    public String guestPlan(
            @RequestParam(defaultValue = "") String guestAddress,
            @RequestParam(defaultValue = DEFAULT_CATEGORY) String category,
            @RequestParam(defaultValue = DEFAULT_ROUTE_MODE) String routeMode,
            Model model
    ) {
        populateGuestPlanModel(model, guestAddress, category, routeMode);
        return "guest/plan";
    }

    @GetMapping("/guest/places")
    public String guestPlaces(
            @RequestParam(defaultValue = "") String originQuery,
            @RequestParam(defaultValue = "") String guestAddress,
            @RequestParam(defaultValue = "") String tag,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "" + DEFAULT_PLACE_DISPLAY_COUNT) Integer display,
            Model model
    ) {
        String effectiveOriginQuery = originQuery == null || originQuery.isBlank() ? guestAddress : originQuery;
        String effectiveTag = tag == null || tag.isBlank() ? category : tag;
        populateGuestPlaceSearchModel(model, effectiveOriginQuery, effectiveTag, display);
        return "guest/places";
    }

    @PostMapping("/guest/preview")
    public String guestPreview(
            @RequestParam(defaultValue = "") String guestAddress,
            @RequestParam(defaultValue = DEFAULT_CATEGORY) String category,
            @RequestParam(defaultValue = "") String detailKeyword,
            @RequestParam(defaultValue = DEFAULT_RECOMMENDATION_MODE) String mode,
            @RequestParam(defaultValue = DEFAULT_ROUTE_MODE) String routeMode,
            RedirectAttributes redirectAttributes
    ) {
        redirectAttributes.addAttribute("guest", true);
        redirectAttributes.addAttribute("guestAddress", guestAddress);
        redirectAttributes.addAttribute("category", category);
        redirectAttributes.addAttribute("detailKeyword", detailKeyword == null ? "" : detailKeyword.trim());
        redirectAttributes.addAttribute("mode", mode);
        redirectAttributes.addAttribute("routeMode", viewHelper.normalizeRouteMode(routeMode));
        return "redirect:/search/results";
    }

    @ResponseBody
    @GetMapping("/api/categories")
    public RecommendationDTO.CategoryResponse apiCategories() {
        return recommendationService.categories();
    }

    @ResponseBody
    @GetMapping("/api/place-tags")
    public PlaceDTO.PlaceTagCatalogResponse apiPlaceTags() {
        return naverPlaceTagCatalog.catalogResponse();
    }

    @ResponseBody
    @PostMapping("/api/recommendations")
    public RecommendationDTO.RecommendationResponse apiRecommendations(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @CookieValue(name = "${app.auth.jwt.access-cookie-name:WM_ACCESS_TOKEN}", required = false) String accessToken,
            @RequestBody RecommendationDTO.RecommendationRequest request
    ) {
        AppUser requester = authService.requireUser(authorization, accessToken);
        return recommendationService.recommend(requester.getId(), request, authService);
    }
    @ResponseBody
    @PostMapping("/api/recommendations/more")
    public RecommendationSupport.VenueSelectionResult apiMoreRecommendations(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @CookieValue(name = "${app.auth.jwt.access-cookie-name:WM_ACCESS_TOKEN}", required = false) String accessToken
    ) {
        AppUser requester = authService.requireUser(
                authorization,
                accessToken
        );

        String key = recommendationMoreService
                .getLatestKey(requester.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "추가 추천 키를 찾을 수 없습니다."
                ));

        return recommendationMoreService.more(
                requester.getId(),
                key
        );
    }

    @ResponseBody
    @PostMapping("/api/places/search")
    public PlaceDTO.PlaceSearchResponse apiSearchPlaces(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @CookieValue(name = "${app.auth.jwt.access-cookie-name:WM_ACCESS_TOKEN}", required = false) String accessToken,
            @RequestBody PlaceDTO.PlaceSearchRequest request
    ) {
        authService.requireUser(authorization, accessToken);
        return apiNaverPlaceSearchService.search(request);
    }

    @ResponseBody
    @PostMapping("/api/public/places/search")
    public PlaceDTO.PlaceSearchResponse apiPublicPlaceSearch(@RequestBody PlaceDTO.PlaceSearchRequest request) {
        return apiNaverPlaceSearchService.search(request);
    }

    @ResponseBody
    @PostMapping("/api/public/recommendations")
    public RecommendationDTO.RecommendationResponse apiPublicRecommendations(@RequestBody RecommendationDTO.GuestRecommendationRequest request) {
        UserDTO.UserResponse guestUser = createGuestUser(request.baseAddress());
        RecommendationDTO.RecommendationRequest recommendationRequest = new RecommendationDTO.RecommendationRequest(
                request.category(),
                request.detailKeyword(),
                List.of(),
                request.mode(),
                request.anchorParticipantId(),
                request.routeMode()
        );
        return recommendationService.recommendForGuest(guestUser, recommendationRequest);
    }

    private void populateGuestPlanModel(Model model, String guestAddress, String category, String routeMode) {
        viewHelper.populateCommon(model, TAB_NEARBY, true);
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("guestAddress", guestAddress);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("detailKeyword", "");
        model.addAttribute("selectedRouteMode", viewHelper.normalizeRouteMode(routeMode));
    }

    private void populateGuestPlaceSearchModel(
            Model model,
            String originQuery,
            String tag,
            Integer display
    ) {
        viewHelper.populateCommon(model, TAB_NEARBY, true);
        model.addAttribute("originQuery", originQuery);
        model.addAttribute("tag", tag);
        model.addAttribute("display", display == null ? DEFAULT_PLACE_DISPLAY_COUNT : display);
        model.addAttribute("categories", viewService.getSelectableCategories());

        if (originQuery == null || originQuery.isBlank() || tag == null || tag.isBlank()) {
            return;
        }

        try {
            PlaceDTO.PlaceSearchResponse placeSearch = apiNaverPlaceSearchService.search(new PlaceDTO.PlaceSearchRequest(originQuery, tag, display));
            model.addAttribute("placeSearch", placeSearch);
            model.addAttribute("placeMapPoints", buildPlaceMapPoints(placeSearch));
        } catch (ResponseStatusException exception) {
            model.addAttribute("placeSearchError", Optional.ofNullable(exception.getReason()).orElse("장소 검색 중 오류가 발생했습니다."));
        }
    }

    private List<RecommendationDTO.MapPointResponse> buildPlaceMapPoints(PlaceDTO.PlaceSearchResponse placeSearch) {
        List<RecommendationDTO.MapPointResponse> mapPoints = new java.util.ArrayList<>();
        mapPoints.add(new RecommendationDTO.MapPointResponse(
                "origin",
                placeSearch.origin().name(),
                placeSearch.origin().address(),
                placeSearch.origin().latitude(),
                placeSearch.origin().longitude(),
                "anchor",
                false
        ));

        for (int index = 0; index < placeSearch.places().size(); index++) {
            PlaceDTO.PlaceCandidateResponse place = placeSearch.places().get(index);
            String address = place.roadAddress() == null || place.roadAddress().isBlank() ? place.address() : place.roadAddress();
            mapPoints.add(new RecommendationDTO.MapPointResponse(
                    "place-" + index,
                    place.name(),
                    address,
                    place.latitude(),
                    place.longitude(),
                    "venue",
                    index == 0
            ));
        }

        return mapPoints;
    }

    private void populateHomeModel(Model model, AppUser currentUser) {
        viewHelper.populateCommon(model, TAB_HOME, false);
        model.addAttribute("profile", viewHelper.toProfile(currentUser));
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("upcomingMeetings", viewService.getUpcomingMeetings(currentUser.getId()));
    }

    private UserDTO.UserResponse createGuestUser(String baseAddress) {
        String normalizedBaseAddress = baseAddress == null || baseAddress.isBlank()
                ? DEFAULT_GUEST_ADDRESS
                : baseAddress.trim();
        return new UserDTO.UserResponse(
                "guest-user",
                "게스트",
                "guest",
                "",
                "GUEST",
                normalizedBaseAddress,
                null
        );
    }

}


