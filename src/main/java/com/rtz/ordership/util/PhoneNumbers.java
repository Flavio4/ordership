package com.rtz.ordership.util;

/**
 * Normaliza teléfonos de Paraguay al formato internacional (+595...), el que usa WhatsApp.
 * Así el mismo número escrito de distintas formas ("0983 625-215", "983625215", "+595983625215")
 * corresponde a un solo cliente.
 */
public final class PhoneNumbers {

    private static final String PY_PREFIX = "595";

    private PhoneNumbers() {
    }

    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }

        boolean international = phone.trim().startsWith("+") || phone.trim().startsWith("00");
        String digits = phone.replaceAll("\\D", "");
        if (phone.trim().startsWith("00")) {
            digits = digits.substring(2);
        }

        if (international) {
            return "+" + digits;
        }
        // 595 983 625215 (12 dígitos, ya con código de país pero sin "+")
        if (digits.startsWith(PY_PREFIX) && digits.length() == 12) {
            return "+" + digits;
        }
        // 0983 625215 (formato local con 0)
        if (digits.startsWith("0") && digits.length() == 10) {
            return "+" + PY_PREFIX + digits.substring(1);
        }
        // 983 625215 (sin el 0)
        if (digits.startsWith("9") && digits.length() == 9) {
            return "+" + PY_PREFIX + digits;
        }
        // Formato desconocido: se guarda solo con los dígitos, sin inventar un código de país
        return digits;
    }
}
