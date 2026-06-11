package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.PlaceDTO;
import com.kopo.wemeet.dto.RecommendationDTO;
import com.kopo.wemeet.dto.RecommendationMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationLocationService {
    // 좌표 계산, 검색 기준점 선택, 지도 경로용 기초 좌표 로직을 담당한다.

    private final ApiOpenRoutingService apiOpenRoutingService;
    private final ApiNaverPlaceSearchService apiNaverPlaceSearchService;
    private final Map<String, RecommendationSupport.GeoPoint> zoneCenters = Map.of(
            "중구", new RecommendationSupport.GeoPoint(37.5636, 126.9866),
            "성수", new RecommendationSupport.GeoPoint(37.5446, 127.0557),
            "공덕", new RecommendationSupport.GeoPoint(37.5441, 126.9518),
            "홍대", new RecommendationSupport.GeoPoint(37.5572, 126.9245),
            "여의도", new RecommendationSupport.GeoPoint(37.5219, 126.9245),
            "용산", new RecommendationSupport.GeoPoint(37.5299, 126.9658),
            "건대", new RecommendationSupport.GeoPoint(37.5400, 127.0693),
            "왕십리", new RecommendationSupport.GeoPoint(37.5611, 127.0373),
            "기본", new RecommendationSupport.GeoPoint(37.5665, 126.9780)
    );

    public RecommendationSupport.GeoPoint resolveParticipantPoint(RecommendationSupport.ParticipantProfile participant) {
        return apiOpenRoutingService.geocodeAddress(participant.baseAddress())
                .map(point -> new RecommendationSupport.GeoPoint(point.latitude(), point.longitude()))
                .orElseGet(() -> zoneCenters.getOrDefault(resolveZone(participant.baseAddress()), zoneCenters.get("기본")));
    }

    public List<RecommendationSupport.GeoPoint> resolveParticipantPoints(List<RecommendationSupport.ParticipantProfile> participants) {
        return participants.stream()
                .map(this::resolveParticipantPoint)
                .toList();
    }

    public RecommendationSupport.GeoPoint calculateMidpoint(List<RecommendationSupport.GeoPoint> participantPoints) {
        double averageLatitude = participantPoints.stream()
                .mapToDouble(RecommendationSupport.GeoPoint::latitude)
                .average()
                .orElse(zoneCenters.get("기본").latitude());
        double averageLongitude = participantPoints.stream()
                .mapToDouble(RecommendationSupport.GeoPoint::longitude)
                .average()
                .orElse(zoneCenters.get("기본").longitude());
        return new RecommendationSupport.GeoPoint(averageLatitude, averageLongitude);
    }

    public RecommendationSupport.SearchAnchor resolveSearchAnchor(
            RecommendationMode mode,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints,
            RecommendationSupport.GeoPoint midpointPoint,
            String anchorParticipantId
    ) {
        if (participants.size() == 1) {
            return new RecommendationSupport.SearchAnchor(participants.get(0).baseAddress(), participantPoints.get(0));
        }

        if (mode == RecommendationMode.ANCHOR) {
            for (int index = 0; index < participants.size(); index++) {
                if (participants.get(index).id().equals(anchorParticipantId)) {
                    return new RecommendationSupport.SearchAnchor(participants.get(index).baseAddress(), participantPoints.get(index));
                }
            }
        }

        Optional<String> reverseGeocodedQuery = Optional.ofNullable(
                apiNaverPlaceSearchService.reverseGeocodeArea(midpointPoint.latitude(), midpointPoint.longitude())
        ).orElse(Optional.empty());
        if (reverseGeocodedQuery.isPresent()) {
            return new RecommendationSupport.SearchAnchor(reverseGeocodedQuery.get(), midpointPoint);
        }

        String commonDistrict = extractCommonDistrict(participants);
        if (!commonDistrict.isBlank()) {
            return new RecommendationSupport.SearchAnchor(commonDistrict, midpointPoint);
        }

        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < participantPoints.size(); index++) {
            double currentDistance = distance(participantPoints.get(index), midpointPoint);
            if (currentDistance < nearestDistance) {
                nearestDistance = currentDistance;
                nearestIndex = index;
            }
        }
        return new RecommendationSupport.SearchAnchor(extractSearchArea(participants.get(nearestIndex).baseAddress()), midpointPoint);
    }

    public RecommendationSupport.GeoPoint resolveAnchorPoint(
            String anchorParticipantId,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints
    ) {
        for (int index = 0; index < participants.size(); index++) {
            if (participants.get(index).id().equals(anchorParticipantId)) {
                return participantPoints.get(index);
            }
        }
        return participantPoints.get(0);
    }

    public String extractArea(String primaryAddress, String fallbackAddress) {
        String address = firstNonBlank(primaryAddress, fallbackAddress, "");
        if (address.isBlank()) {
            return "근처";
        }
        String[] tokens = address.split("\\s+");
        if (tokens.length >= 2) {
            return tokens[1];
        }
        return tokens[0];
    }

    public List<RecommendationDTO.RoutePointResponse> straightRoutePath(
            RecommendationSupport.GeoPoint origin,
            RecommendationSupport.GeoPoint destination
    ) {
        return List.of(
                new RecommendationDTO.RoutePointResponse(origin.latitude(), origin.longitude()),
                new RecommendationDTO.RoutePointResponse(destination.latitude(), destination.longitude())
        );
    }

    public List<RecommendationDTO.RoutePointResponse> toRecommendationRoutePath(
            List<PlaceDTO.PlaceRoutePointResponse> routePath,
            RecommendationSupport.GeoPoint origin,
            RecommendationSupport.GeoPoint destination
    ) {
        if (routePath == null || routePath.size() < 2) {
            return straightRoutePath(origin, destination);
        }
        return routePath.stream()
                .map(point -> new RecommendationDTO.RoutePointResponse(point.latitude(), point.longitude()))
                .toList();
    }

    public List<RecommendationDTO.RoutePointResponse> toRecommendationRoutePathFromCoordinates(
            List<ApiOpenRoutingService.MapCoordinate> routePath,
            RecommendationSupport.GeoPoint origin,
            RecommendationSupport.GeoPoint destination
    ) {
        if (routePath == null || routePath.size() < 2) {
            return straightRoutePath(origin, destination);
        }
        return routePath.stream()
                .map(point -> new RecommendationDTO.RoutePointResponse(point.latitude(), point.longitude()))
                .toList();
    }

    public double distance(RecommendationSupport.GeoPoint first, RecommendationSupport.GeoPoint second) {
        double latGap = first.latitude() - second.latitude();
        double lonGap = first.longitude() - second.longitude();
        return Math.sqrt(latGap * latGap + lonGap * lonGap);
    }

    public double distanceKilometers(RecommendationSupport.GeoPoint origin, RecommendationSupport.GeoPoint destination) {
        double latKm = Math.abs(origin.latitude() - destination.latitude()) * 111d;
        double lonKm = Math.abs(origin.longitude() - destination.longitude()) * 88d;
        return Math.sqrt((latKm * latKm) + (lonKm * lonKm));
    }

    private String extractCommonDistrict(List<RecommendationSupport.ParticipantProfile> participants) {
        LinkedHashSet<String> districts = participants.stream()
                .map(RecommendationSupport.ParticipantProfile::baseAddress)
                .map(this::extractAdministrativeDistrict)
                .filter(district -> !district.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (districts.size() == 1) {
            return districts.iterator().next();
        }
        return "";
    }

    private String extractSearchArea(String address) {
        if (address == null || address.isBlank()) {
            return "서울";
        }
        String district = extractAdministrativeDistrict(address);
        if (!district.isBlank()) {
            return district;
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length >= 2) {
            return tokens[0] + " " + tokens[1];
        }
        return tokens[0];
    }

    private String extractAdministrativeDistrict(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length >= 2) {
            return tokens[0] + " " + tokens[1];
        }
        return tokens[0];
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String resolveZone(String baseAddress) {
        if (baseAddress == null) {
            return "기본";
        }
        if (baseAddress.contains("명동") || baseAddress.contains("중구")) {
            return "중구";
        }
        if (baseAddress.contains("성수") || baseAddress.contains("성동구")) {
            return "성수";
        }
        if (baseAddress.contains("공덕") || baseAddress.contains("마포구")) {
            return "공덕";
        }
        if (baseAddress.contains("홍대") || baseAddress.contains("연남")) {
            return "홍대";
        }
        if (baseAddress.contains("여의도")) {
            return "여의도";
        }
        if (baseAddress.contains("용산")) {
            return "용산";
        }
        if (baseAddress.contains("건대") || baseAddress.contains("광진구")) {
            return "건대";
        }
        if (baseAddress.contains("왕십리") || baseAddress.contains("행당동") || baseAddress.contains("상왕십리")) {
            return "왕십리";
        }
        return "기본";
    }
}


