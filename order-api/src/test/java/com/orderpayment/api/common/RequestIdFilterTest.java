package com.orderpayment.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("doFilter 는 요청 ID 헤더가 있으면 해당 값을 응답과 MDC에 사용한다")
    void doFilter_whenRequestIdHeaderExists_thenUseClientRequestIdAndStoreInMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestTracing.REQUEST_ID_HEADER, "request-123");

        filter.doFilter(request, response, assertMdcRequestId("request-123"));

        assertThat(response.getHeader(RequestTracing.REQUEST_ID_HEADER)).isEqualTo("request-123");
        assertThat(MDC.get(RequestTracing.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("doFilter 는 요청 ID 헤더가 없으면 새 요청 ID를 생성한다")
    void doFilter_whenRequestIdHeaderMissing_thenGenerateRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String requestId = MDC.get(RequestTracing.REQUEST_ID_MDC_KEY);
            assertThat(((HttpServletResponse) servletResponse).getHeader(RequestTracing.REQUEST_ID_HEADER))
                    .isEqualTo(requestId);
        });

        assertThat(MDC.get(RequestTracing.REQUEST_ID_MDC_KEY)).isNull();
    }

    private static FilterChain assertMdcRequestId(String expectedRequestId) {
        return (ServletRequest request, ServletResponse response) ->
                assertThat(MDC.get(RequestTracing.REQUEST_ID_MDC_KEY)).isEqualTo(expectedRequestId);
    }
}
