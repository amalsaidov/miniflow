package com.omernaci.miniflow.node.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "seen_keys", uniqueConstraints =
        @UniqueConstraint(name = "uk_seen_key", columnNames = {"workflow_id", "key_value"}))
public class SeenKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, length = 100)
    private String workflowId;

    @Column(name = "key_value", nullable = false, length = 255)
    private String keyValue;

    @Column(name = "seen_at", nullable = false)
    private LocalDateTime seenAt;

    protected SeenKeyEntity() {
    }

    public SeenKeyEntity(String workflowId, String keyValue) {
        this.workflowId = workflowId;
        this.keyValue = keyValue;
        this.seenAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public String getKeyValue() { return keyValue; }
    public LocalDateTime getSeenAt() { return seenAt; }
}
