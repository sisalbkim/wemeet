package com.kopo.wemeet.util;

/**
 * CmmUtil는 여러 계층에서 반복되는 화면/문자열 처리 로직을 모아 둔 유틸리티입니다.
 */
public final class CmmUtil {
    // 문자열 null 처리와 공백 기본값 보정을 위한 공용 유틸이다.

    private CmmUtil() {
    }

    public static String nvl(String str) {
        return nvl(str, "");
    }

    public static String nvl(String str, String defaultValue) {
        if (str == null || str.isBlank()) {
            return defaultValue;
        }

        return str.trim();
    }
}
