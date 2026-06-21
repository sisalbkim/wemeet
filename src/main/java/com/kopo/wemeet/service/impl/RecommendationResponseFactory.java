package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.PlaceDTO;
import com.kopo.wemeet.dto.RecommendationDTO;
import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RecommendationResponseFactory는 도메인 규칙과 외부 연동 흐름을 조합해 실제 비즈니스 처리를 수행합니다.
 */
@Component
@RequiredArgsConstructor
public class RecommendationResponseFactory {
    // 추천 계산 결과를 최종 응답 DTO로 조립한다.

    private final RecommendationLocationService locationService;

    public RecommendationDTO.RecommendationResponse buildRecommendation(
            String category,
            List<UserDTO.UserResponse> participantResponses,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints,
            RecommendationSupport.GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId,
            PlaceDTO.PlaceSearchResponse placeSearch,
            RecommendationSupport.SearchAnchor searchAnchor,
            List<RecommendationSupport.VenueEvaluation> selectedEvaluations,
            List<UserDTO.UserResponse> excludedParticipantResponses
    ) {
        List<RecommendationDTO.VenueResponse> venues = selectedEvaluations.stream()
                .map(RecommendationSupport.VenueEvaluation::response)
                .toList();
        RecommendationDTO.VenueResponse bestVenue = venues.get(0);
        boolean usedFallbackRouting = selectedEvaluations.stream().anyMatch(RecommendationSupport.VenueEvaluation::usedFallbackRouting);
        RecommendationSupport.GeoPoint referencePoint = mode == RecommendationMode.ANCHOR
                ? locationService.resolveAnchorPoint(anchorParticipantId, participants, participantPoints)
                : midpointPoint;

        RecommendationDTO.MidpointResponse midpoint = new RecommendationDTO.MidpointResponse(
                locationService.extractArea(placeSearch.origin().address(), searchAnchor.query()),
                placeSearch.origin().name(),
                referencePoint.latitude(),
                referencePoint.longitude(),
                bestVenue.averageMinutes(),
                bestVenue.fairnessGap(),
                strategyNote(mode, participants.size(), usedFallbackRouting)
        );

        return new RecommendationDTO.RecommendationResponse(
                category,
                participantResponses,
                midpoint,
                venues,
                buildMapPoints(participants, participantPoints, midpointPoint, venues, mode, anchorParticipantId),
                calculationModeLabel(mode, usedFallbackRouting),
                excludedParticipantResponses
        );
    }

    public RecommendationDTO.RecommendationResponse buildEmptyRecommendation(
            String category,
            List<UserDTO.UserResponse> participantResponses,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints,
            RecommendationSupport.GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId,
            PlaceDTO.PlaceSearchResponse placeSearch,
            RecommendationSupport.SearchAnchor searchAnchor,
            List<UserDTO.UserResponse> excludedParticipantResponses
    ) {
        RecommendationSupport.GeoPoint referencePoint = mode == RecommendationMode.ANCHOR
                ? locationService.resolveAnchorPoint(anchorParticipantId, participants, participantPoints)
                : midpointPoint;

        return new RecommendationDTO.RecommendationResponse(
                category,
                participantResponses,
                new RecommendationDTO.MidpointResponse(
                        locationService.extractArea(placeSearch.origin().address(), searchAnchor.query()),
                        placeSearch.origin().name(),
                        referencePoint.latitude(),
                        referencePoint.longitude(),
                        0,
                        0,
                        "네이버 검색 결과가 없어 출발지 기준 정보만 표시합니다."
                ),
                List.of(),
                buildMapPoints(participants, participantPoints, midpointPoint, List.of(), mode, anchorParticipantId),
                calculationModeLabel(mode, false),
                excludedParticipantResponses
        );
    }

    public RecommendationDTO.RecommendationResponse buildUnavailableRecommendation(
            String category,
            List<UserDTO.UserResponse> participantResponses,
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints,
            RecommendationSupport.GeoPoint midpointPoint,
            RecommendationMode mode,
            String anchorParticipantId,
            RecommendationSupport.SearchAnchor searchAnchor,
            List<UserDTO.UserResponse> excludedParticipantResponses
    ) {
        RecommendationSupport.GeoPoint referencePoint = mode == RecommendationMode.ANCHOR
                ? locationService.resolveAnchorPoint(anchorParticipantId, participants, participantPoints)
                : midpointPoint;

        return new RecommendationDTO.RecommendationResponse(
                category,
                participantResponses,
                new RecommendationDTO.MidpointResponse(
                        locationService.extractArea(searchAnchor.query(), searchAnchor.query()),
                        searchAnchor.query(),
                        referencePoint.latitude(),
                        referencePoint.longitude(),
                        0,
                        0,
                        "네이버 장소 검색을 완료하지 못해 출발지 기준 정보만 표시합니다."
                ),
                List.of(),
                buildMapPoints(participants, participantPoints, midpointPoint, List.of(), mode, anchorParticipantId),
                calculationModeLabel(mode, true),
                excludedParticipantResponses
        );
    }

    private List<RecommendationDTO.MapPointResponse> buildMapPoints(
            List<RecommendationSupport.ParticipantProfile> participants,
            List<RecommendationSupport.GeoPoint> participantPoints,
            RecommendationSupport.GeoPoint midpointPoint,
            List<RecommendationDTO.VenueResponse> venues,
            RecommendationMode mode,
            String anchorParticipantId
    ) {
        List<RecommendationDTO.MapPointResponse> mapPoints = new ArrayList<>();
        boolean singleParticipant = participants.size() == 1;

        for (int index = 0; index < participants.size(); index++) {
            RecommendationSupport.ParticipantProfile participant = participants.get(index);
            RecommendationSupport.GeoPoint point = participantPoints.get(index);
            mapPoints.add(new RecommendationDTO.MapPointResponse(
                    participant.id(),
                    participant.nickname(),
                    participant.baseAddress(),
                    point.latitude(),
                    point.longitude(),
                    participant.id().equals(anchorParticipantId) && mode == RecommendationMode.ANCHOR ? "anchor" : "participant",
                    false
            ));
        }

        if (!singleParticipant && (mode == RecommendationMode.CENTER || mode == RecommendationMode.RANDOM)) {
            mapPoints.add(new RecommendationDTO.MapPointResponse(
                    "midpoint",
                    "참가자 중심점",
                    "참가자 좌표 평균 중심",
                    midpointPoint.latitude(),
                    midpointPoint.longitude(),
                    "midpoint",
                    false
            ));
        }

        for (int index = 0; index < venues.size(); index++) {
            RecommendationDTO.VenueResponse venue = venues.get(index);
            mapPoints.add(new RecommendationDTO.MapPointResponse(
                    "venue-" + index,
                    venue.name(),
                    venue.description(),
                    venue.latitude(),
                    venue.longitude(),
                    "venue",
                    index == 0
            ));
        }

        return mapPoints;
    }

    private String calculationModeLabel(RecommendationMode mode, boolean usedFallbackRouting) {
        return mode.label() + " · " + (usedFallbackRouting
                ? "장소 검색과 보조 이동시간 기준"
                : "장소 검색과 길찾기 기준");
    }

    private String strategyNote(RecommendationMode mode, int participantCount, boolean usedFallbackRouting) {
        String routingNote = usedFallbackRouting
                ? "일부 후보는 네이버 길찾기 대신 좌표 기반 보조 추정값으로 계산했습니다."
                : "네이버 지역검색과 길찾기 기준으로 이동시간을 계산했습니다.";

        return switch (mode) {
            case CENTER -> participantCount == 1
                    ? "1인 검색은 출발지 주변 실제 네이버 장소검색 결과를 우선 보여줍니다. " + routingNote
                    : "참가자 중심 좌표를 기준으로 네이버 실제 장소를 찾고 전체 이동시간을 다시 정렬했습니다. " + routingNote;
            case ANCHOR -> "기준 인물 주변 네이버 실제 장소를 먼저 찾고, 기준 인물 이동 부담을 더 크게 반영했습니다. " + routingNote;
            case RANDOM -> "중심점 근처 네이버 실제 장소 후보군 안에서 랜덤하게 보여줍니다. " + routingNote;
        };
    }
}
