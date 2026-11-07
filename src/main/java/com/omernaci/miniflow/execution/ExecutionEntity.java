package com.omernaci.miniflow.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "executions", indexes = {
        @Index(name = "idx_exec_started", columnList = "started_at"),
        @Index(name = "idx_exec_workflow", columnList = "workflow_id, started_at")
})
public class ExecutionEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "workflow_id", nullable = false, length = 100)
    private String workflowId;

    @Column(name = "workflow_name")
    private String workflowName;

    @Column(name = "run_mode", nullable = false, length = 20)
    private String runMode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "trigger_json", columnDefinition = "text")
    private String triggerJson;

    @Column(name = "steps_json", columnDefinition = "text")
    private String stepsJson;

    protected ExecutionEntity() {
    }

    public ExecutionEntity(String id, String workflowId, String workflowName, String runMode, String status,
                           LocalDateTime startedAt, long durationMs, String triggerJson, String stepsJson) {
        this.id = id;
        this.workflowId = workflowId;
        this.workflowName = workflowName;
        this.runMode = runMode;
        this.status = status;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
        this.triggerJson = triggerJson;
        this.stepsJson = stepsJson;
    }

    public String getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public String getWorkflowName() { return workflowName; }
    public String getRunMode() { return runMode; }
    public String getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public long getDurationMs() { return durationMs; }
    public String getTriggerJson() { return triggerJson; }
    public String getStepsJson() { return stepsJson; }
}
