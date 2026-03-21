package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.OpenApiProperties;
import com.kopo.wemeet.repository.WemeetDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OpenApiRoutingService {
    // 외부 지도 REST API 호출을 전담한다.
    // 주소 -> 좌표 변환과 출발지/도착지 이동시간 계산을 여기서 감싼다.

    private static final Logger log = LoggerFactory.getLogger(OpenApiRoutingService.class);
    private static final List<String> REGION_KEYWORDS = List.of(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
            "특별시", "광역시", "특별자치시", "특별자치도", "대한민국"
    );

    private final OpenApiProperties properties;
    private final RestClient nominatimClient;
    private final RestClient osrmClient;
    private final Map<String, Coordinate> geocodeCache = new ConcurrentHashMap<>();

    public OpenApiRoutingService(OpenApiProperties properties) {
        this.properties = properties;
        this.nominatimClient = RestClient.builder()
                .baseUrl(properties.getNominatimBaseUrl())
                .defaultHeader("User-Agent", properties.getUserAgent())
                .build();
        this.osrmClient = RestClient.builder()
                .baseUrl(properties.getOsrmBaseUrl())
                .defaultHeader("User-Agent", properties.getUserAgent())
                .build();
    }

    public Optional<Map<String, Integer>> estimateTravelMinutes(
            List<WemeetDataStore.UserAccount> participants,
            String venueAddress
    ) {
        // Open API 사용이 꺼져 있거나 호출이 실패하면 Optional.empty()로 fallback을 유도한다.
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        try {
            Optional<Coordinate> destination = geocodeInternal(venueAddress);
            if (destination.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Integer> travelMinutes = new LinkedHashMap<>();
        for (WemeetDataStore.UserAccount participant : participants) {
                // 각 참가자 주소를 좌표로 바꾼 뒤 목적지까지 예상 시간을 계산한다.
                Optional<Coordinate> origin = geocodeInternal(participant.baseAddress());
                if (origin.isEmpty()) {
                    return Optional.empty();
                }
                Optional<Integer> minutes = routeMinutes(origin.get(), destination.get());
                if (minutes.isEmpty()) {
                    return Optional.empty();
                }
                travelMinutes.put(participant.id(), minutes.get());
            }
            return Optional.of(travelMinutes);
        } catch (RuntimeException exception) {
            log.warn("External routing API call failed. Falling back to heuristic calculation.", exception);
            return Optional.empty();
        }
    }

    public Optional<MapCoordinate> geocodeAddress(String query) {
        // 내부 캐시와 외부 API 상세 구현을 감추고 좌표만 노출한다.
        return geocodeInternal(query)
                .map(coordinate -> new MapCoordinate(coordinate.latitude(), coordinate.longitude()));
    }

    private Optional<Coordinate> geocodeInternal(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String normalizedQuery = normalizeQuery(query);
        Coordinate cached = geocodeCache.get(normalizedQuery);
        if (cached != null) {
            // 동일 주소는 반복 호출이 많아서 메모리 캐시를 먼저 확인한다.
            return Optional.of(cached);
        }

        NominatimSearchResponse[] response = nominatimClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("format", "jsonv2")
                        .queryParam("limit", 1)
                        .queryParam("accept-language", "ko")
                        .queryParam("countrycodes", "kr")
                        .queryParam("q", normalizedQuery)
                        .build())
                .retrieve()
                .body(NominatimSearchResponse[].class);

        if (response == null || response.length == 0) {
            return Optional.empty();
        }

        Coordinate coordinate = new Coordinate(
                Double.parseDouble(response[0].lat()),
                Double.parseDouble(response[0].lon())
        );
        geocodeCache.put(normalizedQuery, coordinate);
        return Optional.of(coordinate);
    }

    private String normalizeQuery(String query) {
        String normalized = query.trim().replaceAll("\\s+", " ");
        if (containsRegionKeyword(normalized)) {
            return normalized;
        }

        if (normalized.contains("명동") || normalized.contains("중구")) {
            return "서울특별시 중구 " + normalized;
        }
        if (normalized.contains("성수")) {
            return "서울특별시 성동구 " + normalized;
        }
        if (normalized.contains("공덕") || normalized.contains("홍대") || normalized.contains("연남")) {
            return "서울특별시 마포구 " + normalized;
        }
        if (normalized.contains("여의도")) {
            return "서울특별시 영등포구 " + normalized;
        }
        if (normalized.contains("용산")) {
            return "서울특별시 용산구 " + normalized;
        }
        if (normalized.contains("건대") || normalized.contains("광진구")) {
            return "서울특별시 광진구 " + normalized;
        }
        if (normalized.contains("왕십리") || normalized.contains("행당동") || normalized.contains("상왕십리")) {
            return "서울특별시 성동구 " + normalized;
        }
        return normalized;
    }

    private boolean containsRegionKeyword(String query) {
        return REGION_KEYWORDS.stream().anyMatch(query::contains);
    }

    private Optional<Integer> routeMinutes(Coordinate origin, Coordinate destination) {
        // OSRM 응답의 duration은 초 단위이므로 분 단위로 바꿔서 사용한다.
        OsrmRouteResponse response = osrmClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/route/v1/{profile}/{fromLon},{fromLat};{toLon},{toLat}")
                        .queryParam("overview", "false")
                        .build(
                                properties.getRouteProfile(),
                                origin.longitude(),
                                origin.latitude(),
                                destination.longitude(),
                                destination.latitude()
                        ))
                .retrieve()
                .body(OsrmRouteResponse.class);

        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            return Optional.empty();
        }

        double seconds = response.routes().get(0).duration();
        return Optional.of((int) Math.max(1, Math.round(seconds / 60.0)));
    }

    private record Coordinate(double latitude, double longitude) {
    }

    public record MapCoordinate(double latitude, double longitude) {
    }

    private record NominatimSearchResponse(String lat, String lon) {
    }

    private record OsrmRouteResponse(List<OsrmRoute> routes) {
    }

    private record OsrmRoute(double duration) {
    }
}
