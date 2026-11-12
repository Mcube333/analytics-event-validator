package com.mudassir.analytics.validate;

import com.mudassir.analytics.model.PiiPolicy;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Decides whether a single attribute value is personal data. */
public final class PiiDetector {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern INDIAN_MOBILE = Pattern.compile("^(?:\\+?91|0)?[6-9]\\d{9}$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{9,14}$");
    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
    private static final Pattern CARD_CANDIDATE = Pattern.compile("^\\d{13,19}$");
    private static final Pattern HASH = Pattern.compile("^(?:[a-fA-F0-9]{32}|[a-fA-F0-9]{64})$");

    private PiiDetector() {
    }

    /** @return the kind of PII found, or empty when the value looks safe. */
    public static Optional<String> detect(String key, Object value, PiiPolicy policy) {
        if (value == null || value instanceof Boolean) {
            return Optional.empty();
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        String leafKey = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (policy.ignoreKeys().contains(leafKey) || policy.ignoreKeys().contains(key.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        if (policy.allowHashed() && HASH.matcher(raw).matches()) {
            return Optional.empty();
        }

        if (EMAIL.matcher(raw).find()) {
            return Optional.of("email address");
        }
        String digits = raw.replaceAll("[\\s()-]", "");
        if (INDIAN_MOBILE.matcher(digits).matches() || E164.matcher(digits).matches()) {
            return Optional.of("phone number");
        }
        if (CARD_CANDIDATE.matcher(digits).matches() && luhn(digits)) {
            return Optional.of("payment card number");
        }
        if (PAN.matcher(raw).find()) {
            return Optional.of("PAN number");
        }
        if (policy.forbiddenKeys().contains(leafKey)) {
            return Optional.of("raw value in forbidden key '" + leafKey + "'");
        }
        return Optional.empty();
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** Masks a value for reports so the report itself does not leak the PII it found. */
    public static String mask(Object value) {
        String s = String.valueOf(value);
        if (s.length() <= 4) {
            return "****";
        }
        return s.substring(0, 2) + "*".repeat(Math.min(8, s.length() - 4)) + s.substring(s.length() - 2);
    }
}
