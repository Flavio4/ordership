package com.rtz.ordership.util;

import java.util.Locale;

public final class SearchPatterns {

    private static final int MIN_PHONE_DIGITS = 6;

    private SearchPatterns() {
    }

    public static String containsLike(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
    }

    public static String phoneContainsLike(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("\\D", "");
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.length() >= MIN_PHONE_DIGITS ? "%" + digits + "%" : null;
    }
}
