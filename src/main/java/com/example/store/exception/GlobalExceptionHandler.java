package com.example.store.exception;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import java.net.URI;
import java.time.Instant;

/**
 * One consistent error shape (RFC 7807 ProblemDetail) across every path that can produce an error response from within
 * a matched handler method. ApiKeyAuthFilter's 401 and IdempotencyFilter's 409 build the same ProblemDetail shape
 * independently, since those are raw servlet-filter writes that happen before Spring MVC dispatch and can't be
 * intercepted here - see SecurityConfig and IdempotencyFilter.
 *
 * <p>Not every DataAccessException means the database is unavailable - a constraint violation is a data problem (409),
 * not an infrastructure one (503). Only failures shaped like "couldn't reach/use the connection" are treated as
 * unavailability; see {@link #isConnectivityFailure}.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        String detail = ex.getReason() != null
                ? ex.getReason()
                : HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase();
        log.info("{} {} rejected: {} {}", request.getMethod(), request.getRequestURI(), ex.getStatusCode(), detail);
        return problemDetail(ex.getStatusCode(), detail, request);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ProblemDetail handleCircuitOpen(CallNotPermittedException ex, HttpServletRequest request) {
        log.warn(
                "Circuit breaker open for {} {} - failing fast without attempting the database",
                request.getMethod(),
                request.getRequestURI());
        return databaseUnavailable(request);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ProblemDetail handleDataAccessFailure(Exception ex, HttpServletRequest request) {
        if (isConnectivityFailure(ex)) {
            log.error("Database connectivity failure on {} {}", request.getMethod(), request.getRequestURI(), ex);
            return databaseUnavailable(request);
        }
        log.warn("Data access error on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return problemDetail(
                HttpStatus.CONFLICT, "The request could not be completed due to a data conflict.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.info("Malformed request body on {} {}", request.getMethod(), request.getRequestURI());
        return problemDetail(HttpStatus.BAD_REQUEST, "Malformed request body.", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    private static boolean isConnectivityFailure(Exception ex) {
        return ex instanceof TransactionException
                || ex instanceof DataAccessResourceFailureException
                || ex instanceof QueryTimeoutException;
    }

    private static ProblemDetail databaseUnavailable(HttpServletRequest request) {
        return problemDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The database is currently unavailable. Please try again shortly.",
                request);
    }

    private static ProblemDetail problemDetail(HttpStatusCode status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
