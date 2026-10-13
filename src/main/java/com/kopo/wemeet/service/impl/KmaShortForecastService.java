
package com.kopo.wemeet.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KmaShortForecastService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private record CacheEntry(JsonNode data, LocalDateTime expiresAt) {}

    private final ConcurrentHashMap<String, CacheEntry> forecastCache =
            new ConcurrentHashMap<>();


    @Value("${weather.kma.short-service-key}")
    private String serviceKey;

    public KmaShortForecastService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create(
                "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0"
        );
        this.objectMapper = objectMapper;
    }

    public JsonNode getForecast(int nx, int ny) {

        // 발표가 완료된 최근 예보 기준시각 사용
        LocalDateTime now = LocalDateTime.now()
                .minusMinutes(20);

        int[] baseHours = {2, 5, 8, 11, 14, 17, 20, 23};

        LocalDateTime base = null;

        for (int i = baseHours.length - 1; i >= 0; i--) {
            if (now.getHour() >= baseHours[i]) {
                base = now.withHour(baseHours[i])
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0);
                break;
            }
        }

        if (base == null) {
            base = now.minusDays(1)
                    .withHour(23)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
        }

        String baseDate = base.format(
                DateTimeFormatter.ofPattern("yyyyMMdd")
        );

        String baseTime = base.format(
                DateTimeFormatter.ofPattern("HHmm")
        );


        String cacheKey = baseDate + "_" + baseTime + "_" + nx + "_" + ny;

        CacheEntry cached = forecastCache.get(cacheKey);

        if (cached != null
                && LocalDateTime.now().isBefore(cached.expiresAt())) {
            return cached.data();
        }

        System.out.println("[단기예보] API 호출: " + cacheKey);

        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getVilageFcst")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("pageNo", 1)
                        .queryParam("numOfRows", 1000)
                        .queryParam("dataType", "JSON")
                        .queryParam("base_date", baseDate)
                        .queryParam("base_time", baseTime)
                        .queryParam("nx", nx)
                        .queryParam("ny", ny)
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
            System.out.println("[단기예보] 캐시 사용: " + cacheKey);
            return result;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "기상청 단기예보 응답 처리 실패", e
            );
        }
    }

    public record WeatherInfo(
            String temperature,
            String sky,
            String precipitationType,
            String rainProbability
    ) {
    }

    public Optional<WeatherInfo> getWeather(
            int nx,
            int ny,
            LocalDate meetingDate,
            LocalTime meetingTime
    ) {
        JsonNode response = getForecast(nx, ny);
        JsonNode items = response.path("response")
                .path("body")
                .path("items")
                .path("item");

        if (!items.isArray()) {
            return Optional.empty();
        }

        String targetDate = meetingDate.format(
                DateTimeFormatter.ofPattern("yyyyMMdd")
        );

        String targetTime = meetingTime.format(
                DateTimeFormatter.ofPattern("HH00")
        );

        String temperature = null;
        String sky = null;
        String precipitationType = null;
        String rainProbability = null;

        for (JsonNode item : items) {
            if (!targetDate.equals(item.path("fcstDate").asString())
                    || !targetTime.equals(item.path("fcstTime").asString())) {
                continue;
            }

            String category = item.path("category").asString();
            String value = item.path("fcstValue").asString();

            switch (category) {
                case "TMP" -> temperature = value;
                case "SKY" -> sky = value;
                case "PTY" -> precipitationType = value;
                case "POP" -> rainProbability = value;
            }
        }

        if (temperature == null) {
            return Optional.empty();
        }

        return Optional.of(new WeatherInfo(
                temperature,
                sky,
                precipitationType,
                rainProbability
        ));
    }


    public String getWeatherDescription(WeatherInfo weather) {

        if (weather == null) {
            return "날씨 정보 없음";
        }

        String condition;

        // 강수 형태를 우선 확인
        condition = switch (weather.precipitationType()) {
            case "1" -> "비";
            case "2" -> "비/눈";
            case "3" -> "눈";
            case "4" -> "소나기";
            default -> switch (weather.sky()) {
                case "1" -> "맑음";
                case "3" -> "구름많음";
                case "4" -> "흐림";
                default -> "정보 없음";
            };
        };

        return weather.temperature() + "°C · "
                + condition + " · 강수확률 "
                + weather.rainProbability() + "%";
    }

}
