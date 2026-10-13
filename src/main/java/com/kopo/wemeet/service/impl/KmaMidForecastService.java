
package com.kopo.wemeet.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KmaMidForecastService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private record CacheEntry(JsonNode data, LocalDateTime expiresAt) {}

    private final ConcurrentHashMap<String, CacheEntry> forecastCache =
            new ConcurrentHashMap<>();


    @Value("${weather.kma.mid-service-key}")
    private String serviceKey;

    public KmaMidForecastService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create(
                "https://apis.data.go.kr/1360000/MidFcstInfoService"
        );
        this.objectMapper = objectMapper;
    }

    // 중기 육상예보 조회
    public JsonNode getMidLandForecast(
            String regId,
            String tmFc
    ) {

        String cacheKey = regId + "_" + tmFc;


        CacheEntry cached = forecastCache.get(cacheKey);

        if (cached != null
                && LocalDateTime.now().isBefore(cached.expiresAt())) {
            System.out.println("[중기예보] 캐시 사용: " + cacheKey);
            return cached.data();
        }


        System.out.println("[중기예보] API 호출: " + cacheKey);
        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getMidLandFcst")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("pageNo", 1)
                        .queryParam("numOfRows", 10)
                        .queryParam("dataType", "JSON")
                        .queryParam("regId", regId)
                        .queryParam("tmFc", tmFc)
                        .build())
                .retrieve()
                .body(String.class);

        try {

            JsonNode result = objectMapper.readTree(response);

            if ("00".equals(result.path("response")
                    .path("header")
                    .path("resultCode")
                    .asString())) {

                forecastCache.put(
                        cacheKey,
                        new CacheEntry(
                                result,
                                LocalDateTime.now().plusMinutes(30)
                        )
                );
            }

            return result;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "기상청 중기예보 응답 처리 실패", e
            );
        }
    }

    public record MidWeatherInfo(
            String weather,
            int rainProbability
    ) {
    }

    public Optional<MidWeatherInfo> getWeather(
            String regId,
            LocalDate meetingDate,
            LocalTime meetingTime
    ) {
        // 최근 중기예보 발표 기준시각
        LocalDateTime now = LocalDateTime.now()
                .minusMinutes(30);

        LocalDateTime base;

        if (now.getHour() >= 18) {
            base = now.withHour(18);
        } else if (now.getHour() >= 6) {
            base = now.withHour(6);
        } else {
            base = now.minusDays(1).withHour(18);
        }

        base = base.withMinute(0)
                .withSecond(0)
                .withNano(0);

        String tmFc = base.format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        );

        // 발표 기준일과 모임 날짜의 차이 계산
        long days = ChronoUnit.DAYS.between(
                base.toLocalDate(),
                meetingDate
        );

        if (days < 4 || days > 7) {
            return Optional.empty();
        }

        JsonNode response = getMidLandForecast(regId, tmFc);
        JsonNode items = response.path("response")
                .path("body")
                .path("items")
                .path("item");

        if (!items.isArray() || items.isEmpty()) {
            return Optional.empty();
        }

        JsonNode item = items.get(0);

        // 오전 12시 이전은 오전, 이후는 오후
        boolean morning = meetingTime.getHour() < 12;

        String suffix = morning ? "Am" : "Pm";
        String day = String.valueOf(days);

        String weather = item.path("wf" + day + suffix).asString();
        int rainProbability = item.path("rnSt" + day + suffix).asInt();

        if (weather == null || weather.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new MidWeatherInfo(
                weather,
                rainProbability
        ));
    }

}
