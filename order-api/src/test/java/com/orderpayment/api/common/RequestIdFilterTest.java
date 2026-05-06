package com.orderpayment.api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void usesClientRequestIdAndStoresItInMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestTracing.REQUEST_ID_HEADER, "request-123");

        filter.doFilter(request, response, assertMdcRequestId("request-123"));

        assertEquals("request-123", response.getHeader(RequestTracing.REQUEST_ID_HEADER));
        assertNull(MDC.get(RequestTracing.REQUEST_ID_MDC_KEY));
    }

    @Test
    void generatesRequestIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String requestId = MDC.get(RequestTracing.REQUEST_ID_MDC_KEY);
            assertEquals(requestId, ((HttpServletResponse) servletResponse).getHeader(RequestTracing.REQUEST_ID_HEADER));
        });

        assertNull(MDC.get(RequestTracing.REQUEST_ID_MDC_KEY));
    }

    private static FilterChain assertMdcRequestId(String expectedRequestId) {
        return (ServletRequest request, ServletResponse response) ->
                assertEquals(expectedRequestId, MDC.get(RequestTracing.REQUEST_ID_MDC_KEY));
    }
}
