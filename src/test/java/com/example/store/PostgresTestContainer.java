package com.example.store;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres 16.2 container (matching production), shared by every test that needs a real database, started once per
 * JVM in the static initializer below and never explicitly stopped - it's reaped by Testcontainers' Ryuk container when
 * the JVM exits. Deliberately not using @Testcontainers/@Container: that JUnit5-extension-managed lifecycle is scoped
 * per test class and doesn't reliably keep a single container alive across unrelated test classes sharing it via
 * inheritance, which caused intermittent "connection refused" failures between repository test classes in CI. This is
 * Testcontainers' own documented "singleton container" pattern.
 */
public abstract class PostgresTestContainer {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.2");

    static {
        POSTGRES.start();
    }
}
