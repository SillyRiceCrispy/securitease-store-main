package com.example.store.resilience;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import java.util.Map;

/**
 * Translates database-unavailability failures into a clean 503 instead of a raw 500: CallNotPermittedException when the
 * circuit breaker is open (the DB is known to be failing, so the call was never attempted), or a
 * DataAccessException/TransactionException that survived retries (reads) or failed outright (writes, which aren't
 * retried - see the services in the service package). Both exception hierarchies show up in practice: a failed query is
 * a DataAccessException, but failing to even open the transaction (no connection available) is a TransactionException -
 * a sibling hierarchy, not a DataAccessException subtype.
 */
@RestControllerAdvice
public class DatabaseUnavailableExceptionHandler {

    @ExceptionHandler({CallNotPermittedException.class, DataAccessException.class, TransactionException.class})
    public ResponseEntity<Map<String, String>> handleDatabaseUnavailable(Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "ServiceUnavailable",
                        "message", "The database is currently unavailable. Please try again shortly."));
    }
}
