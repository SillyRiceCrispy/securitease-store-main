package com.example.store.idempotency;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    @Mock
    private IdempotencyRecordRepository repository;

    @InjectMocks
    private IdempotencyFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("api-client", null, List.of()));
    }

    @Test
    void passesThroughUnauthenticatedRequestsUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customer");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(repository, never()).findByIdempotencyKeyAndRequestPath(any(), any());
    }

    @Test
    void passesThroughRequestsWithoutTheHeader() throws Exception {
        authenticate();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(repository, never()).findByIdempotencyKeyAndRequestPath(any(), any());
    }

    @Test
    void passesThroughNonPostRequestsEvenWithHeader() throws Exception {
        authenticate();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/customer");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(repository, never()).findByIdempotencyKeyAndRequestPath(any(), any());
    }

    @Test
    void replaysCachedResponseForAnAlreadyCompletedKey() throws Exception {
        authenticate();
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setResponseStatus(201);
        existing.setResponseBody("{\"id\":1}");
        when(repository.findByIdempotencyKeyAndRequestPath("key-1", "/customer"))
                .thenReturn(Optional.of(existing));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customer");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":1}");
    }

    @Test
    void rejectsWithConflictWhenSameKeyStillInProgress() throws Exception {
        authenticate();
        IdempotencyRecord inProgress = new IdempotencyRecord();
        inProgress.setResponseStatus(null);
        when(repository.findByIdempotencyKeyAndRequestPath("key-1", "/customer"))
                .thenReturn(Optional.of(inProgress));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customer");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    void rejectsWithConflictWhenConcurrentRequestWinsTheRace() throws Exception {
        authenticate();
        when(repository.findByIdempotencyKeyAndRequestPath("key-1", "/customer"))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customer");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    void storesTheResponseAfterANewRequestCompletes() throws Exception {
        authenticate();
        when(repository.findByIdempotencyKeyAndRequestPath("key-1", "/customer"))
                .thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customer");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            res.setContentType("application/json");
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
            res.getWriter().write("{\"id\":1}");
        };

        filter.doFilterInternal(request, response, chain);

        verify(repository).saveAndFlush(any());
        verify(repository).save(any());
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":1}");
    }

    @Test
    void deletesThePlaceholderInsteadOfCachingATransientServerError() throws Exception {
        authenticate();
        when(repository.findByIdempotencyKeyAndRequestPath("key-1", "/customer"))
                .thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customer");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(500);

        filter.doFilterInternal(request, response, chain);

        verify(repository).delete(any());
        verify(repository, never()).save(any());
    }
}
