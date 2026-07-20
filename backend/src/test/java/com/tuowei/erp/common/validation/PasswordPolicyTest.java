package com.tuowei.erp.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsPasswordsAtBcryptByteLimit() {
        String password = "Password123" + "中".repeat(20);

        assertThatCode(() -> PasswordPolicy.assertValid(password, "password"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordsAboveBcryptByteLimit() {
        String password = "Password123" + "中".repeat(21);

        assertThatThrownBy(() -> PasswordPolicy.assertValid(password, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PasswordPolicy.LENGTH_MESSAGE);
    }
}
