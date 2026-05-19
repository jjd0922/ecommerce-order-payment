package com.orderpayment.api.common;

import org.slf4j.MDC;

public final class RequestTracing {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String SPAN_ID_MDC_KEY = "spanId";
    public static final String ORDER_ID_MDC_KEY = "orderId";
    public static final String PAYMENT_ID_MDC_KEY = "paymentId";

    private RequestTracing() {
    }

    public static String currentRequestId() {
        return MDC.get(REQUEST_ID_MDC_KEY);
    }

    public static void putOrderId(Object orderId) {
        put(ORDER_ID_MDC_KEY, orderId);
    }

    public static void putPaymentId(Object paymentId) {
        put(PAYMENT_ID_MDC_KEY, paymentId);
    }

    public static void removeOrderId() {
        MDC.remove(ORDER_ID_MDC_KEY);
    }

    public static void removePaymentId() {
        MDC.remove(PAYMENT_ID_MDC_KEY);
    }

    private static void put(String key, Object value) {
        if (value != null) {
            MDC.put(key, value.toString());
        }
    }
}
