package com.tuowei.erp.common.validation;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 72;
    public static final int MAX_BCRYPT_BYTES = 72;
    public static final String CONTENT_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)\\S+$";
    public static final String LENGTH_MESSAGE = "密码长度必须在12到72位之间";
    public static final String CONTENT_MESSAGE = "密码必须包含字母和数字，且不能包含空白字符";
    private static final Pattern CONTENT = Pattern.compile(CONTENT_PATTERN);

    private PasswordPolicy() {
    }

    public static void assertValid(String password, String fieldName) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (password.length() < MIN_LENGTH
                || password.length() > MAX_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new IllegalArgumentException(LENGTH_MESSAGE);
        }
        if (!CONTENT.matcher(password).matches()) {
            throw new IllegalArgumentException(CONTENT_MESSAGE);
        }
    }
}
