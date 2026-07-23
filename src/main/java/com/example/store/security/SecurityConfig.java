package com.example.store.security;

import com.example.store.idempotency.IdempotencyFilter;
import com.example.store.idempotency.IdempotencyRecordRepository;
import com.example.store.logging.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.net.URI;
import java.time.Instant;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    @Value("${app.security.api-key}")
    private String apiKey;

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ApiKeyAuthFilter apiKeyAuthFilter = new ApiKeyAuthFilter(apiKey);
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // liveness/readiness are polled by container orchestrators and CI smoke
                        // tests that don't (and shouldn't need to) send an API key
                        .requestMatchers("/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // ApiKeyAuthFilter's position must be established (relative to the well-known
                // UsernamePasswordAuthenticationFilter) before it can be used as an anchor for
                // the other two filters below.
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Runs first so every subsequent filter, and every log line for this request,
                // can be correlated - including rejected/unauthenticated requests.
                .addFilterBefore(new RequestIdFilter(), ApiKeyAuthFilter.class)
                // Runs after API-key auth so it can see whether the request is authenticated,
                // and only spends a DB round trip on requests that are actually going to reach
                // a controller.
                .addFilterAfter(
                        new IdempotencyFilter(idempotencyRecordRepository, objectMapper), ApiKeyAuthFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    log.warn("Rejected unauthenticated request: {} {}", request.getMethod(), request.getRequestURI());
                    ProblemDetail problem =
                            ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Missing or invalid API key");
                    problem.setInstance(URI.create(request.getRequestURI()));
                    problem.setProperty("timestamp", Instant.now());
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    objectMapper.writeValue(response.getWriter(), problem);
                }));
        return http.build();
    }
}
