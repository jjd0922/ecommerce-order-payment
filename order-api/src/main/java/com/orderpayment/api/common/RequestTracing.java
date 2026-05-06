package com.orderpayment.api.common;

import org.slf4j.MDC;

public final class RequestTracing {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private RequestTracing() {
    }

    public static String currentRequestId() {
        return MDC.get(REQUEST_ID_MDC_KEY);
    }
}
