package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.*;

import com.kopo.wemeet.config.OpenApiProperties;
import com.kopo.wemeet.config.OpenApiRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * ApiNaverPlaceSearchService는 도메인 규칙과 외부 연동 흐름을 조합해 실제 비즈니스 처리를 수행합니다.
 */
@Service
public class ApiNaverPlaceSearchService {
    // 네이버 지역검색과 지도 길찾기를 묶어서 장소 후보 조회와 거리 정렬을 담당한다.

    private static final Logger log = LoggerFactory.getLogger(ApiNaverPlaceSearchService.class);
    private static final int DEFAULT_DISPLAY = 5;
    private static final int MAX_DISPLAY = 5;
    private static final int MAX_CANDIDATES_FOR_ROUTING = 8;
    private static final double NAVER_SCALED_COORDINATE_DIVISOR = 10_000_000d;

    private final OpenApiProperties properties;
    private final NaverPlaceTagCatalog tagCatalog;
    private final RestClient naverSearchClient;
    private final RestClient naverMapsClient;

    public ApiNaverPlaceSearchService(
            OpenApiProperties properties,
            NaverPlaceTagCatalog tagCatalog,
            OpenApiRestClientFactory restClientFactory
    ) {
        this.properties = properties;
        this.tagCatalog = tagCatalog;
        this.naverSearchClient = restClientFactory.create(
                properties.getNaverSearch().getBaseUrl(),
                builder -> builder
                        .defaultHeader("X-Naver-Client-Id", properties.getNaverSearch().getClientId())
                        .defaultHeader("X-Naver-Client-Secret", properties.getNaverSearch().getClientSecret())
        );
        this.naverMapsClient = restClientFactory.create(
                properties.getNaverMaps().getBaseUrl(),
                builder -> builder
                        .defaultHeader("x-ncp-apigw-api-key-id", properties.getNaverMaps().getApiKeyId())
                        .defaultHeader("x-ncp-apigw-api-key", properties.getNaverMaps().getApiKey())
        );
    }

    public PlaceDTO.PlaceSearchResponse search(PlaceDTO.PlaceSearchRequest request) {
        // 출발지 해석 -> 태그 확장 -> 장소 검색 -> 길찾기 기반 정렬 순서로 동작한다.
        if (!properties.isEnabled() || !properties.isNaverSearchConfigured() || !properties.isNaverMapsConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Naver search/maps API credentials are not configured.");
        }

        String originQuery = requireText(request.originQuery(), "originQuery");
        String tag = requireText(request.tag(), "tag");
        String effectiveTag = firstNonBlank(request.detailKeyword(), tag);
        int display = normalizeDisplay(request.display());
        NaverPlaceTagCatalog.ResolvedTag resolvedTag = tagCatalog.resolve(effectiveTag);

        ResolvedPlace origin = resolveOrigin(originQuery);
        String combinedQuery = originQuery + " " + resolvedTag.primaryQueryTerm();

        List<PlaceDTO.PlaceCandidateResponse> places = searchCandidates(originQuery, origin, display, resolvedTag).stream()
                .map(item -> mapCandidate(origin, item))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(PlaceDTO.PlaceCandidateResponse::durationMinutes)
                        .thenComparingInt(PlaceDTO.PlaceCandidateResponse::distanceMeters)
                        .thenComparing(PlaceDTO.PlaceCandidateResponse::name))
                .limit(display)
                .toList();
        List<String> observedCategories = places.stream()
                .map(PlaceDTO.PlaceCandidateResponse::category)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .toList();

        log.info(
                "Naver place search tag mapping applied. originQuery={}, requestedTag={}, normalizedTag={}, appliedQueryTerms={}, observedCategories={}",
                originQuery,
                effectiveTag,
                resolvedTag.normalizedTag(),
                resolvedTag.queryTerms(),
                observedCategories
        );

        return new PlaceDTO.PlaceSearchResponse(
                combinedQuery,
                effectiveTag,
                resolvedTag.normalizedTag(),
                resolvedTag.queryTerms(),
                observedCategories,
                new PlaceDTO.PlaceSearchOriginResponse(
                        originQuery,
                        origin.name(),
                        origin.address(),
                        origin.latitude(),
                        origin.longitude()
                ),
                places
        );
    }

    public Optional<Integer> estimateTravelMinutes(
            String originQuery,
            double destinationLatitude,
            double destinationLongitude
    ) {
        // 추천 서비스가 후보별 참가자 이동시간을 다시 계산할 때 재사용하는 보조 메서드다.
        return estimateTravelRoute(originQuery, destinationLatitude, destinationLongitude)
                .map(RouteEstimate::durationMinutes);
    }

    public Optional<RouteEstimate> estimateTravelRoute(
            String originQuery,
            double destinationLatitude,
            double destinationLongitude
    ) {
        // 추천 지도에서 참가자별 경로선을 그릴 수 있도록 시간과 경로 좌표를 함께 반환한다.
        if (!properties.isEnabled() || !properties.isNaverMapsConfigured()) {
            return Optional.empty();
        }

        try {
            ResolvedPlace origin = resolveOrigin(originQuery);
            RouteSummary route = route(origin, destinationLatitude, destinationLongitude);
            return Optional.of(new RouteEstimate(route.durationMinutes(), route.path()));
        } catch (RuntimeException exception) {
            log.warn("Failed to estimate travel minutes with Naver Directions. originQuery={}", originQuery, exception);
            return Optional.empty();
        }
    }

    public Optional<String> reverseGeocodeArea(double latitude, double longitude) {
        // 중심 좌표를 사람이 읽기 쉬운 행정구역 문자열로 바꿔 검색 anchor 후보로 쓴다.
        if (!properties.isEnabled() || !properties.isNaverMapsConfigured()) {
            return Optional.empty();
        }

        try {
            ReverseGeocodeResponse response = naverMapsClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getNaverMaps().getReverseGeocodePath())
                            .queryParam("request", "coordsToaddr")
                            .queryParam("coords", longitude + "," + latitude)
                            .queryParam("sourcecrs", "epsg:4326")
                            .queryParam("orders", "roadaddr,addr,admcode")
                            .queryParam("output", "json")
                            .build())
                    .retrieve()
                    .body(ReverseGeocodeResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return Optional.empty();
            }

            for (ReverseGeocodeResult result : response.results()) {
                String area = buildAdministrativeArea(result.region());
                if (!area.isBlank()) {
                    return Optional.of(area);
                }
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("Failed to reverse geocode midpoint with Naver API. latitude={}, longitude={}", latitude, longitude, exception);
            return Optional.empty();
        }
    }

    private List<LocalSearchItem> searchCandidates(
            String originQuery,
            ResolvedPlace origin,
            int display,
            NaverPlaceTagCatalog.ResolvedTag resolvedTag
    ) {
        // 여러 검색 베이스와 태그 조합을 순회하면서 중복 없는 후보를 최대치까지 모은다.
        LinkedHashMap<String, LocalSearchItem> deduplicated = new LinkedHashMap<>();
        List<String> searchBases = buildSearchBases(originQuery, origin);

        for (String base : searchBases) {
            for (String queryTerm : resolvedTag.queryTerms()) {
                String combinedQuery = base + " " + queryTerm;
                for (LocalSearchItem item : localSearch(combinedQuery, display)) {
                    deduplicated.putIfAbsent(candidateKey(item), item);
                    if (deduplicated.size() >= MAX_CANDIDATES_FOR_ROUTING) {
                        return List.copyOf(deduplicated.values());
                    }
                }
            }
        }

        return List.copyOf(deduplicated.values());
    }

    private List<String> buildSearchBases(String originQuery, ResolvedPlace origin) {
        LinkedHashSet<String> bases = new LinkedHashSet<>();
        String trimmedOriginQuery = originQuery.trim();
        boolean addressLike = trimmedOriginQuery.matches(".*\\d+.*");
        String coarseArea = extractCoarseArea(origin.address());
        String districtArea = extractDistrictArea(origin.address());

        if (addressLike) {
            if (!districtArea.isBlank()) {
                bases.add(districtArea);
            }
            if (!coarseArea.isBlank()) {
                bases.add(coarseArea);
            }
            bases.add(trimmedOriginQuery);
        } else {
            bases.add(trimmedOriginQuery);
            if (!districtArea.isBlank()) {
                bases.add(districtArea);
            }
            if (!coarseArea.isBlank()) {
                bases.add(coarseArea);
            }
        }

        return new ArrayList<>(bases);
    }

    private String extractCoarseArea(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length >= 2) {
            return tokens[0] + " " + tokens[1];
        }
        return tokens[0];
    }

    private String extractDistrictArea(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length >= 3 && isNeighborhoodToken(tokens[2])) {
            return tokens[1] + " " + tokens[2];
        }
        if (tokens.length >= 2) {
            return tokens[1];
        }
        return tokens[0];
    }

    private boolean isNeighborhoodToken(String token) {
        return token.endsWith("동") || token.endsWith("가") || token.endsWith("읍") || token.endsWith("면") || token.endsWith("리");
    }

    private ResolvedPlace resolveOrigin(String originQuery) {
        // 주소 형태면 geocode 우선, 아니면 지역검색 우선으로 출발지를 해석한다.
        if (isAddressLikeQuery(originQuery)) {
            Optional<ResolvedPlace> geocoded = geocodeOrigin(originQuery);
            if (geocoded.isPresent()) {
                return geocoded.get();
            }
        }

        List<LocalSearchItem> localMatches = localSearch(originQuery, 1);
        if (!localMatches.isEmpty()) {
            LocalSearchItem item = localMatches.get(0);
            return new ResolvedPlace(
                    sanitizeText(item.title()),
                    primaryAddress(item),
                    parseNaverLongitude(item.mapx()),
                    parseNaverLatitude(item.mapy())
            );
        }

        return geocodeOrigin(originQuery)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Origin place could not be resolved."));
    }

    private Optional<ResolvedPlace> geocodeOrigin(String originQuery) {
        GeocodeResponse geocodeResponse = naverMapsClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(properties.getNaverMaps().getGeocodePath())
                        .queryParam("query", originQuery)
                        .build())
                .retrieve()
                .body(GeocodeResponse.class);

        if (geocodeResponse == null || geocodeResponse.addresses() == null || geocodeResponse.addresses().isEmpty()) {
            return Optional.empty();
        }

        GeocodeAddress address = geocodeResponse.addresses().get(0);
        return Optional.of(new ResolvedPlace(
                originQuery,
                firstNonBlank(address.roadAddress(), address.jibunAddress(), originQuery),
                parseCoordinate(address.x()),
                parseCoordinate(address.y())
        ));
    }

    private List<LocalSearchItem> localSearch(String query, int display) {
        LocalSearchResponse response = naverSearchClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/search/local.json")
                        .queryParam("query", query)
                        .queryParam("display", display)
                        .queryParam("start", 1)
                        .build())
                .retrieve()
                .body(LocalSearchResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items();
    }

    private PlaceDTO.PlaceCandidateResponse mapCandidate(ResolvedPlace origin, LocalSearchItem item) {
        // 네이버 검색 결과 한 건을 길찾기 포함 후보 DTO로 바꾼다.
        try {
            double longitude = parseNaverLongitude(item.mapx());
            double latitude = parseNaverLatitude(item.mapy());
            RouteSummary route = route(origin, latitude, longitude);

            return new PlaceDTO.PlaceCandidateResponse(
                    sanitizeText(item.title()),
                    tagCatalog.normalizeCategory(item.category()),
                    sanitizeText(item.category()),
                    sanitizeText(item.address()),
                    sanitizeText(item.roadAddress()),
                    sanitizeText(item.telephone()),
                    item.link(),
                    latitude,
                    longitude,
                    route.distanceMeters(),
                    route.durationMinutes(),
                    route.path()
            );
        } catch (RuntimeException exception) {
            log.warn("Skipping Naver local search candidate because route lookup failed. title={}", item.title(), exception);
            return null;
        }
    }

    private String candidateKey(LocalSearchItem item) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        parts.add(sanitizeText(item.title()));
        parts.add(sanitizeText(item.roadAddress()));
        parts.add(sanitizeText(item.address()));
        return String.join("|", parts);
    }

    private RouteSummary route(ResolvedPlace origin, double destinationLatitude, double destinationLongitude) {
        String routeOption = properties.getNaverMaps().getRouteOption();
        DirectionsResponse response = naverMapsClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(properties.getNaverMaps().getDirectionsPath())
                        .queryParam("start", origin.longitude() + "," + origin.latitude())
                        .queryParam("goal", destinationLongitude + "," + destinationLatitude)
                        .queryParam("option", routeOption)
                        .build())
                .retrieve()
                .body(DirectionsResponse.class);

        if (response == null || response.route() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Naver directions response is empty.");
        }

        List<DirectionsRoute> routes = response.route().get(routeOption);
        if (routes == null || routes.isEmpty() || routes.get(0).summary() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Naver directions route is empty.");
        }

        DirectionsRoute route = routes.get(0);
        int durationMinutes = (int) Math.max(1, Math.round(route.summary().duration() / 60000d));
        List<PlaceDTO.PlaceRoutePointResponse> path = route.path() == null
                ? List.of()
                : route.path().stream()
                .filter(point -> point.size() >= 2)
                .map(point -> new PlaceDTO.PlaceRoutePointResponse(point.get(1), point.get(0)))
                .toList();

        return new RouteSummary(route.summary().distance(), durationMinutes, path);
    }

    private int normalizeDisplay(Integer requestedDisplay) {
        if (requestedDisplay == null) {
            return DEFAULT_DISPLAY;
        }
        return Math.max(1, Math.min(MAX_DISPLAY, requestedDisplay));
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required.");
        }
        return value.trim();
    }

    private String sanitizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutTags = value.replaceAll("<[^>]+>", "");
        return HtmlUtils.htmlUnescape(withoutTags).trim();
    }

    private String primaryAddress(LocalSearchItem item) {
        return firstNonBlank(item.roadAddress(), item.address(), sanitizeText(item.title()));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return sanitizeText(value);
            }
        }
        return "";
    }

    private boolean isAddressLikeQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return query.matches(".*\\d+.*")
                || query.contains("구")
                || query.contains("동")
                || query.contains("로")
                || query.contains("길");
    }

    private String buildAdministrativeArea(ReverseGeocodeRegion region) {
        if (region == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        appendAreaName(parts, region.area1());
        appendAreaName(parts, region.area2());
        appendAreaName(parts, region.area3());
        return String.join(" ", parts).trim();
    }

    private void appendAreaName(List<String> parts, ReverseGeocodeArea area) {
        if (area == null || area.name() == null || area.name().isBlank() || "kr".equalsIgnoreCase(area.name())) {
            return;
        }
        parts.add(area.name().trim());
    }

    private double parseNaverLongitude(String raw) {
        return parseCoordinate(raw);
    }

    private double parseNaverLatitude(String raw) {
        return parseCoordinate(raw);
    }

    private double parseCoordinate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Coordinate is missing from Naver API response.");
        }

        double value = Double.parseDouble(raw);
        if (Math.abs(value) > 1000) {
            return value / NAVER_SCALED_COORDINATE_DIVISOR;
        }
        return value;
    }

    private record LocalSearchResponse(List<LocalSearchItem> items) {
    }

    private record LocalSearchItem(
            String title,
            String link,
            String category,
            String telephone,
            String address,
            String roadAddress,
            String mapx,
            String mapy
    ) {
    }

    private record DirectionsResponse(Map<String, List<DirectionsRoute>> route) {
        private DirectionsResponse {
            if (route == null) {
                route = new LinkedHashMap<>();
            }
        }
    }

    private record DirectionsRoute(
            DirectionsSummary summary,
            List<List<Double>> path
    ) {
    }

    private record DirectionsSummary(
            int distance,
            long duration
    ) {
    }

    private record GeocodeResponse(List<GeocodeAddress> addresses) {
    }

    private record GeocodeAddress(
            String roadAddress,
            String jibunAddress,
            String x,
            String y
    ) {
    }

    private record ReverseGeocodeResponse(List<ReverseGeocodeResult> results) {
    }

    private record ReverseGeocodeResult(
            String name,
            ReverseGeocodeRegion region
    ) {
    }

    private record ReverseGeocodeRegion(
            ReverseGeocodeArea area0,
            ReverseGeocodeArea area1,
            ReverseGeocodeArea area2,
            ReverseGeocodeArea area3,
            ReverseGeocodeArea area4
    ) {
    }

    private record ReverseGeocodeArea(String name) {
    }

    private record ResolvedPlace(
            String name,
            String address,
            double longitude,
            double latitude
    ) {
    }

    private record RouteSummary(
            int distanceMeters,
            int durationMinutes,
            List<PlaceDTO.PlaceRoutePointResponse> path
    ) {
    }

    public record RouteEstimate(
            int durationMinutes,
            List<PlaceDTO.PlaceRoutePointResponse> routePath
    ) {
    }
}

