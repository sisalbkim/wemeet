package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.*;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NaverPlaceTagCatalog는 도메인 규칙과 외부 연동 흐름을 조합해 실제 비즈니스 처리를 수행합니다.
 */
@Component
public class NaverPlaceTagCatalog {
    // 내부 카테고리를 네이버 검색어 스타일로 확장하고, 응답 category를 다시 내부 분류로 정규화한다.

    private final Map<String, TagProfile> profiles;

    public NaverPlaceTagCatalog() {
        this.profiles = new LinkedHashMap<>();
        profiles.put("카페", new TagProfile(
                "카페",
                List.of("카페", "디저트", "베이커리", "커피", "케이크", "브런치카페"),
                List.of("카페", "디저트", "베이커리", "커피", "케이크", "도넛", "빙수", "마카롱", "와플", "브런치카페", "티룸", "로스터리")
        ));
        profiles.put("맛집", new TagProfile(
                "맛집",
                List.of("맛집", "한식", "고기집", "브런치", "파스타", "일식"),
                List.of("한식", "양식", "중식", "일식", "분식", "육류", "고기", "브런치", "파스타", "돈가스", "식당", "국밥", "초밥", "샤브", "오마카세", "치킨", "피자", "햄버거", "족발", "보쌈", "곱창")
        ));
        profiles.put("놀이", new TagProfile(
                "놀이",
                List.of("보드게임카페", "방탈출", "볼링장", "오락실", "노래방", "VR"),
                List.of("보드게임", "방탈출", "볼링", "오락실", "노래방", "VR", "테마파크", "놀거리", "멀티방", "만화카페", "사격장", "당구장")
        ));
        profiles.put("문화", new TagProfile(
                "문화",
                List.of("미술관", "전시", "박물관", "공연장", "갤러리", "영화관"),
                List.of("미술관", "전시", "박물관", "공연장", "갤러리", "문화", "아트", "영화관", "복합문화공간", "서점", "독립서점", "연극", "뮤지컬")
        ));
        profiles.put("운동", new TagProfile(
                "운동",
                List.of("공원", "클라이밍", "헬스장", "필라테스", "요가", "러닝"),
                List.of("공원", "클라이밍", "헬스", "필라테스", "요가", "러닝", "스포츠", "체육", "테니스", "풋살", "수영장", "자전거")
        ));
        profiles.put("기타", new TagProfile(
                "기타",
                List.of("복합문화공간", "쇼핑몰", "팝업", "핫플", "편집샵", "플래그십"),
                List.of("복합문화공간", "쇼핑몰", "팝업", "편집샵", "플래그십", "서점", "핫플", "전시장", "라이프스타일", "소품샵", "백화점", "아울렛")
        ));
    }

    public ResolvedTag resolve(String requestedTag) {
        String normalized = normalizeText(requestedTag);
        if (normalized.isBlank()) {
            TagProfile fallback = profiles.get("기타");
            return new ResolvedTag("", fallback.label(), fallback.queryTerms());
        }

        TagProfile direct = profiles.get(normalized);
        if (direct != null) {
            return new ResolvedTag(normalized, direct.label(), direct.queryTerms());
        }

        TagProfile matched = profiles.values().stream()
                .filter(profile -> profile.queryTerms().stream().anyMatch(term -> term.equalsIgnoreCase(normalized)))
                .findFirst()
                .orElse(null);
        if (matched != null) {
            return new ResolvedTag(normalized, matched.label(), prependRequested(normalized, matched.queryTerms()));
        }

        return new ResolvedTag(normalized, "직접입력", List.of(normalized));
    }

    public String normalizeCategory(String rawCategory) {
        String normalized = normalizeText(rawCategory);
        if (normalized.isBlank()) {
            return "기타";
        }

        String bestLabel = "기타";
        int bestScore = -1;
        for (TagProfile profile : profiles.values()) {
            int score = profile.matchScore(normalized);
            if (score > bestScore) {
                bestScore = score;
                bestLabel = profile.label();
            }
        }
        return bestScore > 0 ? bestLabel : "기타";
    }

    public PlaceDTO.PlaceTagCatalogResponse catalogResponse() {
        return new PlaceDTO.PlaceTagCatalogResponse(
                profiles.values().stream()
                        .map(profile -> new PlaceDTO.PlaceTagResponse(profile.label(), profile.queryTerms()))
                        .toList()
        );
    }

    private List<String> prependRequested(String requested, List<String> queryTerms) {
        if (queryTerms.isEmpty() || requested.equalsIgnoreCase(queryTerms.get(0))) {
            return queryTerms;
        }
        java.util.ArrayList<String> combined = new java.util.ArrayList<>();
        combined.add(requested);
        combined.addAll(queryTerms);
        return List.copyOf(combined);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.KOREA);
    }

    public record ResolvedTag(
            String requestedTag,
            String normalizedTag,
            List<String> queryTerms
    ) {
        // 사용자가 입력한 태그가 실제 네이버 검색에 어떤 질의어 묶음으로 변환됐는지 표현한다.
        public String primaryQueryTerm() {
            return queryTerms.isEmpty() ? requestedTag : queryTerms.get(0);
        }
    }

    private record TagProfile(
            String label,
            List<String> queryTerms,
            List<String> categoryKeywords
    ) {
        // 내부 카테고리 하나가 어떤 검색어/판별 키워드 세트를 갖는지 정의한다.
        private int matchScore(String rawCategory) {
            String normalizedCategory = rawCategory.toLowerCase(Locale.KOREA);
            return categoryKeywords.stream()
                    .filter(keyword -> normalizedCategory.contains(keyword.toLowerCase(Locale.KOREA)))
                    .mapToInt(String::length)
                    .max()
                    .orElse(-1);
        }
    }
}
