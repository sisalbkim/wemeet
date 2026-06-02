package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.RecommendationDTO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MeetingPreviewSessionService {
    // 모임 생성 전 추천 결과 미리보기를 세션에 저장하고 복원한다.

    private static final String MEETING_PREVIEW_SNAPSHOTS = "MEETING_PREVIEW_SNAPSHOTS";

    private final ObjectMapper objectMapper;

    public MeetingPreviewSessionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String rememberPreview(HttpSession session, RecommendationDTO.RecommendationBundle recommendation) {
        if (session == null) {
            return "";
        }

        try {
            String previewKey = UUID.randomUUID().toString();
            Map<String, String> previews = previewStore(session);
            previews.put(previewKey, objectMapper.writeValueAsString(recommendation));
            while (previews.size() > 5) {
                String oldestKey = previews.keySet().iterator().next();
                previews.remove(oldestKey);
            }
            session.setAttribute(MEETING_PREVIEW_SNAPSHOTS, previews);
            return previewKey;
        } catch (Exception exception) {
            return "";
        }
    }

    public String consumePreview(HttpSession session, String previewKey) {
        if (session == null || previewKey == null || previewKey.isBlank()) {
            return null;
        }

        Map<String, String> previews = previewStore(session);
        String snapshotJson = previews.remove(previewKey);
        session.setAttribute(MEETING_PREVIEW_SNAPSHOTS, previews);
        return snapshotJson == null || snapshotJson.isBlank() ? null : snapshotJson;
    }

    public RecommendationDTO.RecommendationBundle findPreview(HttpSession session, String previewKey) {
        if (session == null || previewKey == null || previewKey.isBlank()) {
            return null;
        }

        String snapshotJson = previewStore(session).get(previewKey);
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(snapshotJson, RecommendationDTO.RecommendationBundle.class);
        } catch (Exception exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> previewStore(HttpSession session) {
        Object cached = session.getAttribute(MEETING_PREVIEW_SNAPSHOTS);
        if (cached instanceof Map<?, ?> cachedMap) {
            Map<String, String> previews = new LinkedHashMap<>();
            cachedMap.forEach((key, value) -> {
                if (key instanceof String stringKey && value instanceof String stringValue) {
                    previews.put(stringKey, stringValue);
                }
            });
            return previews;
        }
        return new LinkedHashMap<>();
    }
}
