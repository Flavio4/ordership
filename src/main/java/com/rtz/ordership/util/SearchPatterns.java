package com.rtz.ordership.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchPatterns {

    private static final int MIN_PHONE_DIGITS = 6;
    private static final Pattern ORDER_NUMBER = Pattern.compile("(?i)p?-?\\s*(\\d{1,9})");

    private SearchPatterns() {
    }

    public static String containsLike(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
    }

    /** Número propio del pedido: "P-1024", "p1024" o "1024". Null si el texto no es un número de pedido. */
    public static Long orderNumber(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = ORDER_NUMBER.matcher(text.trim());
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
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
