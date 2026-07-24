package com.example.store.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real security filter chain end to end (unlike the @WebMvcTest controller tests, which disable it with
 * addFilters = false) against a real database, to catch exactly the class of bug that unit-testing each filter in
 * isolation cannot: a wrong relative filter order. That already happened once during development - SecurityConfig
 * failed to even start up because RequestIdFilter was registered as an anchor before its own position had been
 * established - and unit tests for the individual filters would have stayed green throughout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityFilterChainIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.2");

    @Value("${app.security.api-key}")
    private String apiKey;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void requestIdFilterRunsBeforeAuthenticationSoEvenRejectedRequestsAreCorrelated() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v1/customer", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().get("X-Request-Id")).isNotEmpty();
    }

    @Test
    void rejectsAnUnauthenticatedRequestWithAProblemDetailBody() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v1/customer", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("Missing or invalid API key");
    }

    @Test
    void healthEndpointIsReachableWithoutAnApiKey() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aValidApiKeyReachesTheController() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);

        ResponseEntity<String> response =
                restTemplate.exchange("/v1/customer", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get("X-Request-Id")).isNotEmpty();
    }
}
