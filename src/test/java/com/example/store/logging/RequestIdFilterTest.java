package com.example.store.logging;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesAndEchoesARequestIdWhenNoneProvided() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringRequest.set(MDC.get(RequestIdFilter.MDC_KEY));

        filter.doFilterInternal(request, response, chain);

        String echoedId = response.getHeader("X-Request-Id");
        assertThat(echoedId).isNotBlank();
        assertThat(mdcDuringRequest.get()).isEqualTo(echoedId);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void reusesCallerProvidedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "caller-supplied-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("caller-supplied-id");
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new RuntimeException("boom");
        };

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, chain))
                .isInstanceOf(RuntimeException.class);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
