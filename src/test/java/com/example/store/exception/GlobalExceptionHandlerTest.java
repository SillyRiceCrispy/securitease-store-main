package com.example.store.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.server.ResponseStatusException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsResponseStatusExceptionToProblemDetail() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/order/99");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");

        ProblemDetail problem = handler.handleResponseStatusException(ex, request);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("Order not found");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/v1/order/99"));
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void usesReasonPhraseWhenNoExplicitReasonGiven() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/order/99");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND);

        ProblemDetail problem = handler.handleResponseStatusException(ex, request);

        assertThat(problem.getDetail()).isEqualTo("Not Found");
    }

    @Test
    void mapsCircuitOpenToServiceUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/order");
        CallNotPermittedException ex =
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("test"));

        ProblemDetail problem = handler.handleCircuitOpen(ex, request);

        assertThat(problem.getStatus()).isEqualTo(503);
    }

    @Test
    void mapsConnectivityFailureToServiceUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/order");
        DataAccessResourceFailureException ex = new DataAccessResourceFailureException("connection refused");

        ProblemDetail problem = handler.handleDataAccessFailure(ex, request);

        assertThat(problem.getStatus()).isEqualTo(503);
    }

    @Test
    void mapsTransactionExceptionToServiceUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/order");
        CannotCreateTransactionException ex = new CannotCreateTransactionException("no connection");

        ProblemDetail problem = handler.handleDataAccessFailure(ex, request);

        assertThat(problem.getStatus()).isEqualTo(503);
    }

    @Test
    void mapsNonConnectivityDataAccessExceptionToConflictNotUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/order");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("unique constraint violated");

        ProblemDetail problem = handler.handleDataAccessFailure(ex, request);

        assertThat(problem.getStatus()).isEqualTo(409);
    }

    @Test
    void mapsUnexpectedExceptionToInternalServerError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/order");

        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("boom"), request);

        assertThat(problem.getStatus()).isEqualTo(500);
    }
}
