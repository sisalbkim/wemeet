package com.kopo.wemeet.controller;

import com.kopo.wemeet.util.WemeetViewHelper;

import com.kopo.wemeet.dto.*;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.service.IApiAuthService;
import com.kopo.wemeet.service.IApiRecommendationService;
import com.kopo.wemeet.service.IWemeetViewService;
import com.kopo.wemeet.service.impl.NaverPlaceSearchService;
import com.kopo.wemeet.service.impl.NaverPlaceTagCatalog;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class MeetController {
    // 홈, 게스트 추천, 장소 검색 같은 메인 화면 진입점을 연결하는 컨트롤러.

    private final IWemeetViewService viewService;
    private final IApiAuthService authService;
    private final IApiRecommendationService recommendationService;
    private final NaverPlaceSearchService naverPlaceSearchService;
    private final NaverPlaceTagCatalog naverPlaceTagCatalog;
    private final WemeetViewHelper viewHelper;

    public MeetController(
            IWemeetViewService viewService,
            IApiAuthService authService,
            IApiRecommendationService recommendationService,
            NaverPlaceSearchService naverPlaceSearchService,
            NaverPlaceTagCatalog naverPlaceTagCatalog,
            WemeetViewHelper viewHelper
    ) {
        this.viewService = viewService;
        this.authService = authService;
        this.recommendationService = recommendationService;
        this.naverPlaceSearchService = naverPlaceSearchService;
        this.naverPlaceTagCatalog = naverPlaceTagCatalog;
        this.viewHelper = viewHelper;
    }

    @GetMapping("/")
    public String landing(Model model, HttpSession session) {
        AppUser currentUser = viewHelper.findLoggedInUser(session);
        if (currentUser != null) {
            populateHomeModel(model, currentUser);
            return "home/index";
        }

        viewHelper.populateCommon(model, "guest-home", true);
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
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(defaultValue = "car") String routeMode,
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
            @RequestParam(defaultValue = "5") Integer display,
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
            @RequestParam(defaultValue = "맛집") String category,
            @RequestParam(defaultValue = "CENTER") String mode,
            @RequestParam(defaultValue = "car") String routeMode,
            RedirectAttributes redirectAttributes
    ) {
        redirectAttributes.addAttribute("guest", true);
        redirectAttributes.addAttribute("guestAddress", guestAddress);
        redirectAttributes.addAttribute("category", category);
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
            @RequestHeader("Authorization") String authorization,
            @RequestBody RecommendationDTO.RecommendationRequest request
    ) {
        AppUser requester = authService.requireUser(authorization);
        return recommendationService.recommend(requester.getId(), request, authService);
    }

    @ResponseBody
    @PostMapping("/api/places/search")
    public PlaceDTO.PlaceSearchResponse apiSearchPlaces(
            @RequestHeader("Authorization") String authorization,
            @RequestBody PlaceDTO.PlaceSearchRequest request
    ) {
        authService.requireUser(authorization);
        return naverPlaceSearchService.search(request);
    }

    @ResponseBody
    @PostMapping("/api/public/places/search")
    public PlaceDTO.PlaceSearchResponse apiPublicPlaceSearch(@RequestBody PlaceDTO.PlaceSearchRequest request) {
        return naverPlaceSearchService.search(request);
    }

    @ResponseBody
    @PostMapping("/api/public/recommendations")
    public RecommendationDTO.RecommendationResponse apiPublicRecommendations(@RequestBody RecommendationDTO.GuestRecommendationRequest request) {
        UserDTO.UserResponse guestUser = createGuestUser(request.baseAddress());
        RecommendationDTO.RecommendationRequest recommendationRequest = new RecommendationDTO.RecommendationRequest(
                request.category(),
                List.of(),
                request.mode(),
                request.anchorParticipantId(),
                request.routeMode()
        );
        return recommendationService.recommendForGuest(guestUser, recommendationRequest);
    }

    private void populateGuestPlanModel(Model model, String guestAddress, String category, String routeMode) {
        viewHelper.populateCommon(model, "nearby", true);
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("guestAddress", guestAddress);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedRouteMode", viewHelper.normalizeRouteMode(routeMode));
    }

    private void populateGuestPlaceSearchModel(
            Model model,
            String originQuery,
            String tag,
            Integer display
    ) {
        viewHelper.populateCommon(model, "nearby", true);
        model.addAttribute("originQuery", originQuery);
        model.addAttribute("tag", tag);
        model.addAttribute("display", display == null ? 5 : display);
        model.addAttribute("categories", viewService.getSelectableCategories());

        if (originQuery == null || originQuery.isBlank() || tag == null || tag.isBlank()) {
            return;
        }

        try {
            PlaceDTO.PlaceSearchResponse placeSearch = naverPlaceSearchService.search(new PlaceDTO.PlaceSearchRequest(originQuery, tag, display));
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
        viewHelper.populateCommon(model, "home", false);
        model.addAttribute("profile", viewHelper.toProfile(currentUser));
        model.addAttribute("categories", viewService.getSelectableCategories());
        model.addAttribute("upcomingMeetings", viewService.getUpcomingMeetings(currentUser.getId()));
    }

    private UserDTO.UserResponse createGuestUser(String baseAddress) {
        String normalizedBaseAddress = baseAddress == null || baseAddress.isBlank()
                ? "서울특별시 중구 명동길 74"
                : baseAddress.trim();
        return new UserDTO.UserResponse(
                "guest-user",
                "게스트",
                "guest",
                "",
                "GUEST",
                normalizedBaseAddress
        );
    }

}
