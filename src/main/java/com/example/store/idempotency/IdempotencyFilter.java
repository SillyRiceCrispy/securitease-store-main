package com.example.store.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * Makes POST requests safe to retry: a client sends an Idempotency-Key header, and a retry with the same key replays
 * the original response instead of repeating the write. Backed by a DB table (not an in-memory map) so it works
 * correctly across multiple instances, and a unique constraint on (key, path) so two concurrent requests with the same
 * key can't both proceed - the loser gets 409 instead of racing the same write.
 *
 * <p>Skipped entirely for non-POST requests, requests without the header, and unauthenticated requests (checked via the
 * SecurityContext this filter must run after in the chain) - no point spending a DB round trip on a request that's
 * going to be rejected anyway.
 */
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyRecordRepository idempotencyRecordRepository, ObjectMapper objectMapper) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        boolean applies = "POST".equalsIgnoreCase(request.getMethod())
                && key != null
                && !key.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() != null;

        if (!applies) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        Optional<IdempotencyRecord> existing =
                idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath(key, path);
        if (existing.isPresent()) {
            replayOrReject(existing.get(), request, response);
            return;
        }

        IdempotencyRecord placeholder = new IdempotencyRecord();
        placeholder.setIdempotencyKey(key);
        placeholder.setRequestPath(path);
        try {
            idempotencyRecordRepository.saveAndFlush(placeholder);
        } catch (DataIntegrityViolationException e) {
            // Lost the race to a concurrent request using the same key.
            log.warn("Idempotency key conflict (concurrent request) for {} {}", request.getMethod(), path);
            writeConflict(request, response);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            int status = wrappedResponse.getStatus();
            if (status >= 500) {
                // Transient failure - don't lock the client out of retrying with the same key.
                log.warn(
                        "Not caching idempotency key for {} {} - request failed with {}",
                        request.getMethod(),
                        path,
                        status);
                idempotencyRecordRepository.delete(placeholder);
            } else {
                placeholder.setResponseStatus(status);
                placeholder.setResponseBody(
                        new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8));
                idempotencyRecordRepository.save(placeholder);
            }
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void replayOrReject(IdempotencyRecord record, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (record.getResponseStatus() == null) {
            log.warn("Idempotency key still in progress for {} {}", request.getMethod(), request.getRequestURI());
            writeConflict(request, response);
            return;
        }
        log.info(
                "Replaying cached response for idempotency key on {} {}", request.getMethod(), request.getRequestURI());
        response.setStatus(record.getResponseStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(record.getResponseBody());
    }

    private void writeConflict(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A request with this Idempotency-Key is already in progress or was already used");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
