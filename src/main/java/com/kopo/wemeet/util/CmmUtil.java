package com.kopo.wemeet.util;

public final class CmmUtil {

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
