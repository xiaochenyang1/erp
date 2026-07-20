package com.tuowei.erp.common.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LimitedContentCaptureResponseWrapperTest {

    @Test
    void capturesOnlyUpToLimitWhileWritingFullOutputStreamToClient() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        LimitedContentCaptureResponseWrapper wrapper = new LimitedContentCaptureResponseWrapper(response, 5);

        wrapper.getOutputStream().write("123456789".getBytes(StandardCharsets.UTF_8));
        wrapper.copyBodyToResponse();

        assertThat(response.getContentAsByteArray()).isEqualTo("123456789".getBytes(StandardCharsets.UTF_8));
        assertThat(wrapper.getCapturedBody()).isEqualTo("12345".getBytes(StandardCharsets.UTF_8));
        assertThat(wrapper.captureOverflowed()).isTrue();
    }

    @Test
    void capturesWriterOutputUsingResponseEncoding() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        LimitedContentCaptureResponseWrapper wrapper = new LimitedContentCaptureResponseWrapper(response, 16);

        wrapper.getWriter().write("中文响应");
        wrapper.copyBodyToResponse();

        assertThat(response.getContentAsString()).isEqualTo("中文响应");
        assertThat(new String(wrapper.getCapturedBody(), StandardCharsets.UTF_8)).isEqualTo("中文响应");
        assertThat(wrapper.captureOverflowed()).isFalse();
    }

    @Test
    void keepsSmallOutputBufferedUntilCopied() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        LimitedContentCaptureResponseWrapper wrapper = new LimitedContentCaptureResponseWrapper(response, 16);

        wrapper.getOutputStream().write("small".getBytes(StandardCharsets.UTF_8));

        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(wrapper.getCapturedBody()).isEqualTo("small".getBytes(StandardCharsets.UTF_8));
        assertThat(wrapper.captureOverflowed()).isFalse();

        wrapper.copyBodyToResponse();

        assertThat(response.getContentAsByteArray()).isEqualTo("small".getBytes(StandardCharsets.UTF_8));
    }
}
