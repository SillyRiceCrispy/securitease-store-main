package com.example.store.resilience;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Logs circuit breaker state transitions (CLOSED/OPEN/HALF_OPEN) as they happen, independent of any single request -
 * otherwise the only signal an operator gets is each individual rejected request after the fact, never the moment the
 * circuit actually tripped or recovered.
 */
@Configuration
@Slf4j
public class ResilienceEventLogging {

    public ResilienceEventLogging(CircuitBreakerRegistry circuitBreakerRegistry) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("database");
        circuitBreaker
                .getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "Circuit breaker '{}' transitioned from {} to {}",
                        circuitBreaker.getName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()));
    }
}
