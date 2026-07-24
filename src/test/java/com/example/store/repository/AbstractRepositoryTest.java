package com.example.store.repository;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared real-database fixture for repository tests: a single Postgres 16.2 container (matching production) reused
 * across every subclass via the static field, with Liquibase running the full schema/data/sequence-sync changelog
 * against it exactly as it would against any other fresh database.
 */
@Testcontainers
abstract class AbstractRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.2");
}
