package com.tuowei.erp.common.idempotency;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

class LimitedContentCaptureResponseWrapper extends HttpServletResponseWrapper {

    private final int captureLimitBytes;
    private final ByteArrayOutputStream capturedBody;

    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private boolean outputStreamObtained;
    private boolean writerObtained;
    private boolean captureOverflowed;
    private boolean bodyReleased;

    LimitedContentCaptureResponseWrapper(HttpServletResponse response, int captureLimitBytes) {
        super(response);
        this.captureLimitBytes = Math.max(captureLimitBytes, 0);
        this.capturedBody = new ByteArrayOutputStream(Math.min(this.captureLimitBytes, 4096));
    }

    byte[] getCapturedBody() {
        flushWriter();
        return capturedBody.toByteArray();
    }

    boolean captureOverflowed() {
        flushWriter();
        return captureOverflowed;
    }

    void copyBodyToResponse() throws IOException {
        flushWriter();
        if (!bodyReleased) {
            capturedBody.writeTo(getResponse().getOutputStream());
            bodyReleased = true;
        }
        getResponse().flushBuffer();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writerObtained) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        outputStreamObtained = true;
        return captureOutputStream();
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStreamObtained) {
            throw new IllegalStateException("getOutputStream() has already been called");
        }
        writerObtained = true;
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(captureOutputStream(), responseCharset()));
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        flushWriter();
        if (bodyReleased) {
            getResponse().flushBuffer();
        }
    }

    private ServletOutputStream captureOutputStream() throws IOException {
        if (outputStream == null) {
            outputStream = new CapturingServletOutputStream(getResponse().getOutputStream());
        }
        return outputStream;
    }

    private void capture(byte[] bytes, int offset, int length, ServletOutputStream delegate) throws IOException {
        if (length < 1) {
            return;
        }
        if (bodyReleased) {
            delegate.write(bytes, offset, length);
            return;
        }

        int remaining = captureLimitBytes - capturedBody.size();
        if (length <= remaining) {
            capturedBody.write(bytes, offset, length);
            return;
        }

        captureOverflowed = true;
        int capturedLength = Math.max(remaining, 0);
        if (capturedLength > 0) {
            capturedBody.write(bytes, offset, capturedLength);
        }
        releaseCapturedBody(delegate);

        int passthroughOffset = offset + capturedLength;
        int passthroughLength = length - capturedLength;
        if (passthroughLength > 0) {
            delegate.write(bytes, passthroughOffset, passthroughLength);
        }
    }

    private void releaseCapturedBody(ServletOutputStream delegate) throws IOException {
        if (!bodyReleased) {
            capturedBody.writeTo(delegate);
            bodyReleased = true;
        }
    }

    private void flushWriter() {
        if (writer != null) {
            writer.flush();
        }
    }

    private Charset responseCharset() {
        String encoding = getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.ISO_8859_1;
        }
        return Charset.forName(encoding);
    }

    private class CapturingServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;

        private CapturingServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            delegate.setWriteListener(writeListener);
        }

        @Override
        public void write(int value) throws IOException {
            byte[] singleByte = {(byte) value};
            write(singleByte, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            capture(bytes, offset, length, delegate);
        }

        @Override
        public void flush() throws IOException {
            if (bodyReleased) {
                delegate.flush();
            }
        }
    }
}
