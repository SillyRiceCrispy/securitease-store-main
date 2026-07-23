package com.example.store.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
public class IdempotencyFilter extends OncePerRequestFilter {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyFilter(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
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
            replayOrReject(existing.get(), response);
            return;
        }

        IdempotencyRecord placeholder = new IdempotencyRecord();
        placeholder.setIdempotencyKey(key);
        placeholder.setRequestPath(path);
        try {
            idempotencyRecordRepository.saveAndFlush(placeholder);
        } catch (DataIntegrityViolationException e) {
            // Lost the race to a concurrent request using the same key.
            writeConflict(response);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            int status = wrappedResponse.getStatus();
            if (status >= 500) {
                // Transient failure - don't lock the client out of retrying with the same key.
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

    private void replayOrReject(IdempotencyRecord record, HttpServletResponse response) throws IOException {
        if (record.getResponseStatus() == null) {
            writeConflict(response);
            return;
        }
        response.setStatus(record.getResponseStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(record.getResponseBody());
    }

    private void writeConflict(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write(
                        "{\"error\":\"Conflict\",\"message\":\"A request with this Idempotency-Key is already in progress or was already used\"}");
    }
}
