package com.tuowei.erp.common.web;

import org.springframework.util.StringUtils;

public final class SafeFilename {

    private SafeFilename() {
    }

    public static String normalize(String filename, String fallback, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        String normalized = sanitize(filename);
        if (normalized.isBlank() || ".".equals(normalized) || "..".equals(normalized)) {
            normalized = sanitize(fallback);
        }
        if (normalized.isBlank() || ".".equals(normalized) || "..".equals(normalized)) {
            normalized = "file";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(normalized.length() - maxLength);
    }

    public static String extensionOf(String filename, String fallbackExtension, int maxExtensionLength) {
        if (maxExtensionLength < 1) {
            throw new IllegalArgumentException("maxExtensionLength must be positive");
        }
        String safeFilename = sanitize(filename);
        int dot = safeFilename.lastIndexOf('.');
        if (dot < 0 || dot == safeFilename.length() - 1) {
            return fallbackExtension;
        }
        String extension = safeFilename.substring(dot);
        return extension.length() <= maxExtensionLength ? extension : fallbackExtension;
    }

    private static String sanitize(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        String raw = filename.trim();
        int lastSeparator = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        String baseName = lastSeparator >= 0 ? raw.substring(lastSeparator + 1).trim() : raw;
        StringBuilder sanitized = new StringBuilder(baseName.length());
        for (int index = 0; index < baseName.length(); index++) {
            char value = baseName.charAt(index);
            sanitized.append(isUnsafeFilenameCharacter(value) ? '_' : value);
        }
        return sanitized.toString().trim();
    }

    private static boolean isUnsafeFilenameCharacter(char value) {
        return Character.isISOControl(value)
                || value == '/'
                || value == '\\'
                || value == ':'
                || value == '*'
                || value == '?'
                || value == '"'
                || value == '<'
                || value == '>'
                || value == '|';
    }
}
