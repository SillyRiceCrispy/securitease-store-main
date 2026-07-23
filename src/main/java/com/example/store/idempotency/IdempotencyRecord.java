package com.example.store.idempotency;

import jakarta.persistence.*;

import lombok.Data;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "idempotency_record")
@Data
public class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "idempotency_record_seq")
    @SequenceGenerator(name = "idempotency_record_seq", sequenceName = "idempotency_record_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_path")
    private String requestPath;

    // Null while the original request is still being processed.
    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
