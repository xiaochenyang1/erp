package com.tuowei.erp.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeaderValueSanitizerTest {

    @Test
    void collapsesControlCharactersAndWhitespace() {
        String sanitized = HeaderValueSanitizer.sanitize("  Mozilla\r\n\tAgent  ", 64);

        assertThat(sanitized).isEqualTo("Mozilla Agent");
    }

    @Test
    void returnsNullWhenValueHasNoVisibleText() {
        assertThat(HeaderValueSanitizer.sanitize("\r\n\t ", 64)).isNull();
    }

    @Test
    void trimsAfterApplyingLengthLimit() {
        String sanitized = HeaderValueSanitizer.sanitize("Mozilla \tAgent", 8);

        assertThat(sanitized).isEqualTo("Mozilla");
    }

    @Test
    void rejectsNonPositiveLengthLimit() {
        assertThatThrownBy(() -> HeaderValueSanitizer.sanitize("value", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxLength must be positive");
    }
}
