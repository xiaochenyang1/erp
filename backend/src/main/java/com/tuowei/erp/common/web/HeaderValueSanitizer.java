package com.tuowei.erp.common.web;

import org.springframework.util.StringUtils;

public final class HeaderValueSanitizer {

    private HeaderValueSanitizer() {
    }

    public static String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive");
        }

        StringBuilder sanitized = new StringBuilder(value.length());
        boolean previousWhitespace = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isISOControl(current) || Character.isWhitespace(current)) {
                if (!previousWhitespace) {
                    sanitized.append(' ');
                    previousWhitespace = true;
                }
                continue;
            }
            sanitized.append(current);
            previousWhitespace = false;
        }

        String normalized = sanitized.toString().trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String truncated = normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim();
        return StringUtils.hasText(truncated) ? truncated : null;
    }
}
