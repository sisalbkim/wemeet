package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.FriendDTO;
import com.kopo.wemeet.dto.RecommendationDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecommendationBundleMapper {
    // 추천 API 응답을 Thymeleaf 화면 전용 번들로 재조립한다.

    public RecommendationDTO.RecommendationBundle toBundle(
            RecommendationDTO.RecommendationResponse response,
            String participantStatusLabel
    ) {
        List<FriendDTO.FriendSummary> participants = response.participants().stream()
                .map(participant -> new FriendDTO.FriendSummary(
                        participant.id(),
                        participant.nickname(),
                        "@" + participant.friendCode(),
                        participant.baseAddress(),
                        participantStatusLabel,
                        false
                ))
                .toList();

        List<RecommendationDTO.VenueOption> venues = response.venues().stream()
                .map(venue -> new RecommendationDTO.VenueOption(
                        venue.name(),
                        venue.category(),
                        venue.area(),
                        venue.latitude(),
                        venue.longitude(),
                        venue.description(),
                        venue.telephone(),
                        venue.link(),
                        venue.reason(),
                        venue.fairnessGap(),
                        venue.averageMinutes(),
                        venue.highlights(),
                        venue.travelTimes().stream()
                                .map(time -> new RecommendationDTO.TravelTime(
                                        time.participantId(),
                                        time.participantName(),
                                        time.minutes(),
                                        time.routePath(),
                                        time.routeModes()
                                ))
                                .toList()
                ))
                .toList();

        return new RecommendationDTO.RecommendationBundle(
                response.category(),
                participants,
                new RecommendationDTO.MidpointSummary(
                        response.midpoint().district(),
                        response.midpoint().station(),
                        response.midpoint().latitude(),
                        response.midpoint().longitude(),
                        response.midpoint().averageMinutes(),
                        response.midpoint().fairnessGap(),
                        response.midpoint().note()
                ),
                venues,
                response.mapPoints().stream()
                        .map(point -> new RecommendationDTO.MapPoint(
                                point.id(),
                                point.label(),
                                point.address(),
                                point.latitude(),
                                point.longitude(),
                                point.markerType(),
                                point.selected()
                        ))
                        .toList(),
                response.calculationMode()
        );
    }
}
