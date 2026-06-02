package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.RecommendationMode;
import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.repository.WemeetDataStore;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RecommendationParticipantService {
    // 참가자 목록 정규화, 카테고리 검증, 캐시 키 생성을 담당한다.

    private static final String DEFAULT_CATEGORY = "맛집";
    private static final List<String> CATEGORIES = List.of("맛집", "카페", "놀이", "문화", "운동", "기타");

    private final WemeetDataStore store;

    public RecommendationParticipantService(WemeetDataStore store) {
        this.store = store;
    }

    public List<String> categories() {
        return CATEGORIES;
    }

    public String normalizeCategory(String category) {
        if (category == null || category.isBlank() || !CATEGORIES.contains(category)) {
            return DEFAULT_CATEGORY;
        }
        return category;
    }

    public List<UserDTO.UserAccount> resolveParticipants(String requesterId, List<String> participantIds) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        uniqueIds.add(requesterId);
        if (participantIds != null) {
            uniqueIds.addAll(participantIds);
        }

        return uniqueIds.stream()
                .map(id -> store.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Participant not found: " + id)))
                .toList();
    }

    public List<RecommendationSupport.ParticipantProfile> toParticipantProfiles(List<UserDTO.UserAccount> participantAccounts) {
        return participantAccounts.stream()
                .map(participant -> new RecommendationSupport.ParticipantProfile(
                        participant.id(),
                        participant.nickname(),
                        participant.baseAddress()
                ))
                .toList();
    }

    public String resolveAnchorParticipantId(
            RecommendationMode mode,
            String requesterId,
            String requestedAnchorId,
            List<RecommendationSupport.ParticipantProfile> participants
    ) {
        if (mode != RecommendationMode.ANCHOR) {
            return requesterId;
        }
        if (requestedAnchorId == null || requestedAnchorId.isBlank()) {
            return requesterId;
        }
        boolean exists = participants.stream().anyMatch(participant -> participant.id().equals(requestedAnchorId));
        return exists ? requestedAnchorId : requesterId;
    }

    public String buildCacheKey(
            String schemaVersion,
            String requesterId,
            String category,
            List<RecommendationSupport.ParticipantProfile> participants
    ) {
        String participantKey = participants.stream()
                .map(participant -> participant.id() + ":" + normalizeAddressForCache(participant.baseAddress()))
                .sorted()
                .collect(Collectors.joining(","));
        return schemaVersion + ":" + requesterId + ":" + category + ":" + participantKey;
    }

    public String normalizeAddressForCache(String address) {
        if (address == null || address.isBlank()) {
            return "empty";
        }
        return address.replaceAll("\\s+", "").trim();
    }
}

