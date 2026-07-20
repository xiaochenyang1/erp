package com.tuowei.erp.testsupport;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ControlledStreamingResponse {

    private final CountDownLatch release = new CountDownLatch(1);
    private final StreamingResponseBody body;

    private ControlledStreamingResponse(String content) {
        this.body = outputStream -> {
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to release streaming response body");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting to release streaming response body", ex);
            }
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        };
    }

    public static ControlledStreamingResponse csv(String content) {
        return new ControlledStreamingResponse(content);
    }

    public StreamingResponseBody body() {
        return body;
    }

    public void release() {
        release.countDown();
    }
}
