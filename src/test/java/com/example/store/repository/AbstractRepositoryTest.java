package com.example.store.repository;

import com.example.store.PostgresTestContainer;

/**
 * Shared real-database fixture for repository tests: Liquibase runs the full schema/data/sequence-sync changelog
 * against the shared container exactly as it would against any other fresh database.
 */
abstract class AbstractRepositoryTest extends PostgresTestContainer {}
