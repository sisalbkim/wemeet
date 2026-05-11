package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.OpenApiProperties;
import com.kopo.wemeet.dto.RecommendationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TmapWalkingRoutingService {
    // TMAP 보행자 경로 API를 호출해 실제 도보 이동시간과 폴리라인을 계산한다.

    private static final Logger log = LoggerFactory.getLogger(TmapWalkingRoutingService.class);

    private final OpenApiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient tmapClient;

    public TmapWalkingRoutingService(OpenApiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.tmapClient = RestClient.builder()
                .baseUrl(properties.getTmap().getBaseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Optional<WalkingRouteEstimate> estimateWalkingRoute(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude
    ) {
        if (!properties.isEnabled() || !properties.isTmapConfigured()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("startX", originLongitude);
            requestBody.put("startY", originLatitude);
            requestBody.put("endX", destinationLongitude);
            requestBody.put("endY", destinationLatitude);
            requestBody.put("reqCoordType", "WGS84GEO");
            requestBody.put("resCoordType", "WGS84GEO");
            requestBody.put("startName", "출발");
            requestBody.put("endName", "도착");
            requestBody.put("searchOption", "0");
            requestBody.put("angle", 20);
            requestBody.put("speed", 30);
            requestBody.put("sort", "index");

            String responseBody = tmapClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getTmap().getPedestrianPath())
                            .queryParam("version", properties.getTmap().getVersion())
                            .build())
                    .header("appKey", properties.getTmap().getAppKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return Optional.ofNullable(parsePedestrianRoute(responseBody));
        } catch (RestClientResponseException exception) {
            log.warn(
                    "TMAP walking API request failed. status={} origin={},{} destination={},{} body={}",
                    exception.getStatusCode().value(),
                    originLatitude,
                    originLongitude,
                    destinationLatitude,
                    destinationLongitude,
                    sanitizeResponseBody(exception.getResponseBodyAsString()),
                    exception
            );
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to estimate walking route with TMAP. origin={},{} destination={},{}",
                    originLatitude,
                    originLongitude,
                    destinationLatitude,
                    destinationLongitude,
                    exception
            );
            return Optional.empty();
        }
    }

    WalkingRouteEstimate parsePedestrianRoute(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode features = root.path("features");
            if (!features.isArray() || features.isEmpty()) {
                return null;
            }

            Integer totalTimeSeconds = null;
            int fallbackTimeSeconds = 0;
            List<RecommendationDTO.RoutePointResponse> routePath = new ArrayList<>();

            for (JsonNode feature : features) {
                JsonNode propertiesNode = feature.path("properties");
                if (totalTimeSeconds == null && propertiesNode.path("totalTime").isNumber()) {
                    totalTimeSeconds = propertiesNode.path("totalTime").asInt();
                }

                JsonNode geometryNode = feature.path("geometry");
                if (!"LineString".equalsIgnoreCase(geometryNode.path("type").asText())) {
                    continue;
                }

                if (propertiesNode.path("time").isNumber()) {
                    fallbackTimeSeconds += propertiesNode.path("time").asInt();
                }

                JsonNode coordinatesNode = geometryNode.path("coordinates");
                if (!coordinatesNode.isArray()) {
                    continue;
                }

                for (JsonNode coordinateNode : coordinatesNode) {
                    if (!coordinateNode.isArray() || coordinateNode.size() < 2) {
                        continue;
                    }
                    JsonNode longitudeNode = coordinateNode.get(0);
                    JsonNode latitudeNode = coordinateNode.get(1);
                    if (longitudeNode == null || latitudeNode == null
                            || !longitudeNode.isNumber() || !latitudeNode.isNumber()) {
                        continue;
                    }
                    appendRoutePoint(routePath, new RecommendationDTO.RoutePointResponse(
                            latitudeNode.asDouble(),
                            longitudeNode.asDouble()
                    ));
                }
            }

            int resolvedSeconds = totalTimeSeconds != null ? totalTimeSeconds : fallbackTimeSeconds;
            if (resolvedSeconds <= 0 || routePath.size() < 2) {
                return null;
            }

            int minutes = Math.max(1, (int) Math.round(resolvedSeconds / 60.0d));
            return new WalkingRouteEstimate(minutes, routePath);
        } catch (Exception exception) {
            log.warn("Failed to parse TMAP walking response body={}", sanitizeResponseBody(responseBody), exception);
            return null;
        }
    }

    private void appendRoutePoint(
            List<RecommendationDTO.RoutePointResponse> routePath,
            RecommendationDTO.RoutePointResponse point
    ) {
        if (!routePath.isEmpty()) {
            RecommendationDTO.RoutePointResponse lastPoint = routePath.get(routePath.size() - 1);
            if (Double.compare(lastPoint.latitude(), point.latitude()) == 0
                    && Double.compare(lastPoint.longitude(), point.longitude()) == 0) {
                return;
            }
        }
        routePath.add(point);
    }

    private String sanitizeResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        return responseBody
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
    }

    public record WalkingRouteEstimate(
            int minutes,
            List<RecommendationDTO.RoutePointResponse> routePath
    ) {
    }
}
