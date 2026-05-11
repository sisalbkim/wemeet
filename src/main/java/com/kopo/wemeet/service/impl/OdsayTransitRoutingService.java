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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OdsayTransitRoutingService {
    // ODSAY API를 호출해 대중교통 이동시간과 경로를 계산하는 서비스다.

    private static final Logger log = LoggerFactory.getLogger(OdsayTransitRoutingService.class);

    private final OpenApiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient odsayClient;

    public OdsayTransitRoutingService(OpenApiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.odsayClient = RestClient.builder()
                .baseUrl(properties.getOdsay().getBaseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Optional<TransitRouteEstimate> estimateTransitRoute(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude
    ) {
        if (!properties.isEnabled() || !properties.isOdsayConfigured()) {
            return Optional.empty();
        }

        try {
            SearchPathInfo searchPath = fetchTransitPath(originLatitude, originLongitude, destinationLatitude, destinationLongitude);
            if (searchPath == null || searchPath.totalTime() == null || searchPath.totalTime() <= 0) {
                log.info(
                        "ODsay transit route unavailable. origin={},{} destination={},{} reason=no-path",
                        originLatitude,
                        originLongitude,
                        destinationLatitude,
                        destinationLongitude
                );
                return Optional.empty();
            }

            List<RecommendationDTO.RoutePointResponse> routePath = fetchLanePath(searchPath.mapObj());
            log.info(
                    "ODsay transit route resolved. origin={},{} destination={},{} totalTime={} mapObj={} pointCount={}",
                    originLatitude,
                    originLongitude,
                    destinationLatitude,
                    destinationLongitude,
                    searchPath.totalTime(),
                    searchPath.mapObj(),
                    routePath.size()
            );
            return Optional.of(new TransitRouteEstimate(
                    Math.max(1, searchPath.totalTime()),
                    routePath
            ));
        } catch (RestClientResponseException exception) {
            log.warn(
                    "ODsay transit API request failed. status={} origin={},{} destination={},{} body={}",
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
                    "Failed to estimate transit route with ODsay. origin={},{} destination={},{}",
                    originLatitude,
                    originLongitude,
                    destinationLatitude,
                    destinationLongitude,
                    exception
            );
            return Optional.empty();
        }
    }

    private SearchPathInfo fetchTransitPath(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude
    ) {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("SX", Double.toString(originLongitude));
        queryParams.put("SY", Double.toString(originLatitude));
        queryParams.put("EX", Double.toString(destinationLongitude));
        queryParams.put("EY", Double.toString(destinationLatitude));
        queryParams.put("OPT", "0");
        queryParams.put("apiKey", properties.getOdsay().getApiKey());

        String responseBody = odsayClient.get()
                .uri(buildOdsayUri(properties.getOdsay().getTransitPath(), queryParams))
                .retrieve()
                .body(String.class);

        return parseTransitPath(responseBody);
    }

    private List<RecommendationDTO.RoutePointResponse> fetchLanePath(String mapObj) {
        if (mapObj == null || mapObj.isBlank()) {
            return List.of();
        }

        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("mapObject", "0:0@" + mapObj);
        queryParams.put("apiKey", properties.getOdsay().getApiKey());

        String responseBody = odsayClient.get()
                .uri(buildOdsayUri(properties.getOdsay().getLoadLanePath(), queryParams))
                .retrieve()
                .body(String.class);

        return parseLanePath(responseBody);
    }

    private URI buildOdsayUri(String path, Map<String, String> queryParams) {
        String normalizedBaseUrl = properties.getOdsay().getBaseUrl().endsWith("/")
                ? properties.getOdsay().getBaseUrl().substring(0, properties.getOdsay().getBaseUrl().length() - 1)
                : properties.getOdsay().getBaseUrl();
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String queryString = queryParams.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + encodeQueryValue(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return URI.create(normalizedBaseUrl + normalizedPath + "?" + queryString);
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    List<RecommendationDTO.RoutePointResponse> parseLanePath(String responseBody) {
        List<RecommendationDTO.RoutePointResponse> routePath = new ArrayList<>();
        if (responseBody == null || responseBody.isBlank()) {
            return routePath;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode laneNode = root.path("result").path("lane");
            appendLanePoints(routePath, laneNode);
            return routePath;
        } catch (Exception exception) {
            log.warn("Failed to parse ODsay loadLane response body={}", sanitizeResponseBody(responseBody), exception);
            return List.of();
        }
    }

    SearchPathInfo parseTransitPath(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode firstPath = root.path("result").path("path");
            if (firstPath.isArray()) {
                firstPath = firstPath.path(0);
            }

            JsonNode infoNode = firstPath.path("info");
            JsonNode totalTimeNode = infoNode.get("totalTime");
            if (totalTimeNode == null || !totalTimeNode.isNumber()) {
                log.info("ODsay transit response missing route info. body={}", sanitizeResponseBody(responseBody));
                return null;
            }

            JsonNode mapObjNode = infoNode.get("mapObj");
            return new SearchPathInfo(
                    totalTimeNode.asInt(),
                    mapObjNode != null && !mapObjNode.isNull() ? mapObjNode.asText() : null
            );
        } catch (Exception exception) {
            log.warn("Failed to parse ODsay transit response body={}", sanitizeResponseBody(responseBody), exception);
            return null;
        }
    }

    private void appendLanePoints(List<RecommendationDTO.RoutePointResponse> routePath, JsonNode laneNode) {
        if (laneNode == null || laneNode.isMissingNode() || laneNode.isNull()) {
            return;
        }

        if (laneNode.isArray()) {
            for (JsonNode laneEntry : laneNode) {
                appendLanePoints(routePath, laneEntry);
            }
            return;
        }

        JsonNode sectionNode = laneNode.path("section");
        if (sectionNode.isArray()) {
            for (JsonNode sectionEntry : sectionNode) {
                appendSectionPoints(routePath, sectionEntry);
            }
        } else {
            appendSectionPoints(routePath, sectionNode);
        }
    }

    private void appendSectionPoints(List<RecommendationDTO.RoutePointResponse> routePath, JsonNode sectionNode) {
        if (sectionNode == null || sectionNode.isMissingNode() || sectionNode.isNull()) {
            return;
        }

        JsonNode graphPosNode = sectionNode.path("graphPos");
        if (!graphPosNode.isArray()) {
            return;
        }

        for (JsonNode pointNode : graphPosNode) {
            JsonNode xNode = pointNode.get("x");
            JsonNode yNode = pointNode.get("y");
            if (xNode == null || yNode == null || !xNode.isNumber() || !yNode.isNumber()) {
                continue;
            }
            appendRoutePoint(routePath, new RecommendationDTO.RoutePointResponse(yNode.asDouble(), xNode.asDouble()));
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

    public record TransitRouteEstimate(
            int minutes,
            List<RecommendationDTO.RoutePointResponse> routePath
    ) {
    }

    record SearchPathInfo(Integer totalTime, String mapObj) {
    }

}
