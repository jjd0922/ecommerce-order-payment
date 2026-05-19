package com.orderpayment.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        String traceId = resolveHeaderOrDefault(request, RequestTracing.TRACE_ID_HEADER, requestId);
        String spanId = resolveHeaderOrDefault(request, RequestTracing.SPAN_ID_HEADER, UUID.randomUUID().toString());
        MDC.put(RequestTracing.REQUEST_ID_MDC_KEY, requestId);
        MDC.put(RequestTracing.TRACE_ID_MDC_KEY, traceId);
        MDC.put(RequestTracing.SPAN_ID_MDC_KEY, spanId);
        response.setHeader(RequestTracing.REQUEST_ID_HEADER, requestId);
        response.setHeader(RequestTracing.TRACE_ID_HEADER, traceId);
        response.setHeader(RequestTracing.SPAN_ID_HEADER, spanId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(RequestTracing.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }

    private static String resolveHeaderOrDefault(HttpServletRequest request, String headerName, String defaultValue) {
        String headerValue = request.getHeader(headerName);
        if (headerValue == null || headerValue.isBlank()) {
            return defaultValue;
        }
        return headerValue;
    }
}
