package com.kopo.wemeet.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class NaverPlaceTagCatalogTest {
    // 네이버 장소 태그 분류 규칙이 의도대로 동작하는지 확인하는 테스트다.

    private final NaverPlaceTagCatalog catalog = new NaverPlaceTagCatalog();

    @Test
    void internalCategoryExpandsToNaverStyleQueryTerms() {
        NaverPlaceTagCatalog.ResolvedTag resolvedTag = catalog.resolve("맛집");

        assertEquals("맛집", resolvedTag.normalizedTag());
        assertIterableEquals(java.util.List.of("맛집", "한식", "고기집", "브런치", "파스타", "일식"), resolvedTag.queryTerms());
    }

    @Test
    void rawNaverCategoryIsNormalizedBackToInternalCategory() {
        assertEquals("카페", catalog.normalizeCategory("카페>디저트카페"));
        assertEquals("카페", catalog.normalizeCategory("브런치카페"));
        assertEquals("카페", catalog.normalizeCategory("음식점>카페,디저트"));
        assertEquals("맛집", catalog.normalizeCategory("한식>육류,고기요리"));
        assertEquals("문화", catalog.normalizeCategory("미술관"));
    }
}
