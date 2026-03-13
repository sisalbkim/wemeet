package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.OpenApiProperties;
import com.kopo.wemeet.repository.InMemoryWemeetStore;
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

    private static final Logger log = LoggerFactory.getLogger(OpenApiRoutingService.class);

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
            List<InMemoryWemeetStore.UserAccount> participants,
            String venueAddress
    ) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        try {
            Optional<Coordinate> destination = geocodeInternal(venueAddress);
            if (destination.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Integer> travelMinutes = new LinkedHashMap<>();
            for (InMemoryWemeetStore.UserAccount participant : participants) {
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
        return geocodeInternal(query)
                .map(coordinate -> new MapCoordinate(coordinate.latitude(), coordinate.longitude()));
    }

    private Optional<Coordinate> geocodeInternal(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        Coordinate cached = geocodeCache.get(query);
        if (cached != null) {
            return Optional.of(cached);
        }

        NominatimSearchResponse[] response = nominatimClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("format", "jsonv2")
                        .queryParam("limit", 1)
                        .queryParam("q", query)
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
        geocodeCache.put(query, coordinate);
        return Optional.of(coordinate);
    }

    private Optional<Integer> routeMinutes(Coordinate origin, Coordinate destination) {
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
