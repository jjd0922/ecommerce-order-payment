package com.orderpayment.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("request tracing headers exist then use headers and store MDC keys")
    void doFilter_whenTracingHeadersExist_thenUseClientHeadersAndStoreInMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestTracing.REQUEST_ID_HEADER, "request-123");
        request.addHeader(RequestTracing.TRACE_ID_HEADER, "trace-123");
        request.addHeader(RequestTracing.SPAN_ID_HEADER, "span-123");

        filter.doFilter(request, response, assertMdcTracing("request-123", "trace-123", "span-123"));

        assertThat(response.getHeader(RequestTracing.REQUEST_ID_HEADER)).isEqualTo("request-123");
        assertThat(response.getHeader(RequestTracing.TRACE_ID_HEADER)).isEqualTo("trace-123");
        assertThat(response.getHeader(RequestTracing.SPAN_ID_HEADER)).isEqualTo("span-123");
        assertMdcCleared();
    }

    @Test
    @DisplayName("tracing headers missing then generate request and span ids")
    void doFilter_whenTracingHeadersMissing_thenGenerateTracingValues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String requestId = MDC.get(RequestTracing.REQUEST_ID_MDC_KEY);
            String traceId = MDC.get(RequestTracing.TRACE_ID_MDC_KEY);
            String spanId = MDC.get(RequestTracing.SPAN_ID_MDC_KEY);
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

            assertThat(requestId).isNotBlank();
            assertThat(traceId).isEqualTo(requestId);
            assertThat(spanId).isNotBlank();
            assertThat(httpResponse.getHeader(RequestTracing.REQUEST_ID_HEADER)).isEqualTo(requestId);
            assertThat(httpResponse.getHeader(RequestTracing.TRACE_ID_HEADER)).isEqualTo(traceId);
            assertThat(httpResponse.getHeader(RequestTracing.SPAN_ID_HEADER)).isEqualTo(spanId);
        });

        assertMdcCleared();
    }

    private static FilterChain assertMdcTracing(String expectedRequestId, String expectedTraceId, String expectedSpanId) {
        return (ServletRequest request, ServletResponse response) -> {
            assertThat(MDC.get(RequestTracing.REQUEST_ID_MDC_KEY)).isEqualTo(expectedRequestId);
            assertThat(MDC.get(RequestTracing.TRACE_ID_MDC_KEY)).isEqualTo(expectedTraceId);
            assertThat(MDC.get(RequestTracing.SPAN_ID_MDC_KEY)).isEqualTo(expectedSpanId);
        };
    }

    private static void assertMdcCleared() {
        assertThat(MDC.get(RequestTracing.REQUEST_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(RequestTracing.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(RequestTracing.SPAN_ID_MDC_KEY)).isNull();
    }
}
