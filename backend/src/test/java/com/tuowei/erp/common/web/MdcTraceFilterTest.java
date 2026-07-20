package com.tuowei.erp.common.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTraceFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesTraceIdWhenRequestHeaderIsMissing() throws ServletException, IOException {
        MdcTraceFilter filter = new MdcTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, new CapturingFilterChain(traceIdInsideChain));

        assertThat(traceIdInsideChain.get()).isNotBlank();
        assertThat(response.getHeader(MdcTraceFilter.TRACE_ID_HEADER)).isEqualTo(traceIdInsideChain.get());
        assertThat(MDC.get(MdcTraceFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void reusesValidIncomingTraceId() throws ServletException, IOException {
        MdcTraceFilter filter = new MdcTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(MdcTraceFilter.TRACE_ID_HEADER, "trace-20260529_abcDEF123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, new CapturingFilterChain(traceIdInsideChain));

        assertThat(traceIdInsideChain.get()).isEqualTo("trace-20260529_abcDEF123");
        assertThat(response.getHeader(MdcTraceFilter.TRACE_ID_HEADER)).isEqualTo("trace-20260529_abcDEF123");
        assertThat(MDC.get(MdcTraceFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void ignoresInvalidIncomingTraceId() throws ServletException, IOException {
        MdcTraceFilter filter = new MdcTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(MdcTraceFilter.TRACE_ID_HEADER, "bad trace id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, new CapturingFilterChain(traceIdInsideChain));

        assertThat(traceIdInsideChain.get())
                .isNotBlank()
                .isNotEqualTo("bad trace id with spaces");
        assertThat(response.getHeader(MdcTraceFilter.TRACE_ID_HEADER)).isEqualTo(traceIdInsideChain.get());
        assertThat(MDC.get(MdcTraceFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    private static class CapturingFilterChain extends MockFilterChain {

        private final AtomicReference<String> traceIdInsideChain;

        private CapturingFilterChain(AtomicReference<String> traceIdInsideChain) {
            this.traceIdInsideChain = traceIdInsideChain;
        }

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                throws IOException, ServletException {
            traceIdInsideChain.set(MDC.get(MdcTraceFilter.TRACE_ID_MDC_KEY));
            super.doFilter(request, response);
        }
    }
}
