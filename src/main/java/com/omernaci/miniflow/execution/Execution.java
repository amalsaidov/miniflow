package com.omernaci.miniflow.execution;

import com.omernaci.miniflow.engine.ExecutionContext.StepLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record Execution(String id, String workflowId, String workflowName, String mode, String status,
                        LocalDateTime startedAt, long durationMs,
                        Map<String, Object> trigger, List<StepLog> steps) {
}
