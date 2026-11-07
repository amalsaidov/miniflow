package com.omernaci.miniflow.execution;

import com.omernaci.miniflow.config.MiniFlowProperties;
import com.omernaci.miniflow.engine.ExecutionContext.StepLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ExecutionStore {

    private static final Logger log = LoggerFactory.getLogger(ExecutionStore.class);
    private static final TypeReference<List<StepLog>> STEPS = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final ExecutionRepository repository;
    private final ObjectMapper mapper;
    private final MiniFlowProperties props;

    public ExecutionStore(ExecutionRepository repository, ObjectMapper mapper, MiniFlowProperties props) {
        this.repository = repository;
        this.mapper = mapper;
        this.props = props;
    }

    public void add(Execution ex) {
        repository.save(new ExecutionEntity(ex.id(), ex.workflowId(), ex.workflowName(), ex.mode(), ex.status(),
                ex.startedAt(), ex.durationMs(), toJson(ex.trigger()), toJson(ex.steps())));
    }

    public List<Execution> recent(int limit) {
        return repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
                .stream().map(this::toModel).toList();
    }

    public List<Execution> recentForWorkflow(String workflowId, int limit) {
        return repository.findByWorkflowIdOrderByStartedAtDesc(workflowId, PageRequest.of(0, limit))
                .stream().map(this::toModel).toList();
    }

    public Page<Execution> page(String workflowId, String status, String mode, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(page, 1) - 1, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<ExecutionEntity> result = repository.search(blankToNull(workflowId), blankToNull(status),
                blankToNull(mode), request);
        if (result.getContent().isEmpty() && result.getTotalElements() > 0 && page > 1) {
            int last = (int) ((result.getTotalElements() - 1) / size);
            result = repository.search(blankToNull(workflowId), blankToNull(status), blankToNull(mode),
                    PageRequest.of(last, size, Sort.by(Sort.Direction.DESC, "startedAt")));
        }
        return result.map(this::toModel);
    }

    public record Stat(String workflowId, String workflowName, String status, String mode,
                       LocalDateTime startedAt, long durationMs) {
    }

    public List<Stat> statsSince(LocalDateTime since) {
        return repository.statsSince(since).stream()
                .map(r -> new Stat((String) r[0], (String) r[1], (String) r[2], (String) r[3],
                        (LocalDateTime) r[4], r[5] == null ? 0L : ((Number) r[5]).longValue()))
                .toList();
    }

    public List<Execution> recentFailures(int limit) {
        return repository.findByStatusOrderByStartedAtDesc("ERROR", PageRequest.of(0, limit))
                .stream().map(this::toModel).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Optional<Execution> find(String id) {
        return repository.findById(id).map(this::toModel);
    }

    public long count() {
        return repository.count();
    }

    public long countFailed() {
        return repository.countByStatus("ERROR");
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanup() {
        int deleted = repository.deleteOlderThan(LocalDateTime.now().minusDays(props.executionRetentionDays()));
        if (deleted > 0) {
            log.info("Deleted {} old execution record(s)", deleted);
        }
    }

    private JsonNode truncateLongTexts(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            List.copyOf(obj.propertyNames()).forEach(name -> {
                JsonNode child = obj.get(name);
                if (child.isString() && child.asString().length() > MAX_STORED_TEXT) {
                    obj.put(name, "…(" + child.asString().length() + " characters, not stored in history)");
                } else {
                    truncateLongTexts(child);
                }
            });
        } else if (node instanceof ArrayNode arr) {
            for (int i = 0; i < arr.size(); i++) {
                JsonNode child = arr.get(i);
                if (child.isString() && child.asString().length() > MAX_STORED_TEXT) {
                    arr.set(i, StringNode.valueOf("…(" + child.asString().length() + " characters, not stored in history)"));
                } else {
                    truncateLongTexts(child);
                }
            }
        }
        return node;
    }

    private Execution toModel(ExecutionEntity e) {
        try {
            Map<String, Object> trigger = e.getTriggerJson() == null ? Map.of() : mapper.readValue(e.getTriggerJson(), MAP);
            String stepsJson = e.getStepsJson();
            List<StepLog> steps = stepsJson == null ? List.of()
                    : stepsJson.startsWith("{")
                    ? List.of(new StepLog("-", "history", "ERROR", null, null, stepsJson, 0))
                    : mapper.readValue(stepsJson, STEPS);
            return new Execution(e.getId(), e.getWorkflowId(), e.getWorkflowName(), e.getRunMode(), e.getStatus(),
                    e.getStartedAt(), e.getDurationMs(), trigger, steps);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Could not read execution record: " + e.getId(), ex);
        }
    }

    static final int MAX_STORED_TEXT = 20_000;

    private String toJson(Object value) {
        try {
            JsonNode tree = mapper.valueToTree(value);
            return mapper.writeValueAsString(truncateLongTexts(tree));
        } catch (JacksonException | IllegalArgumentException ex) {
            String msg = String.valueOf(ex.getMessage()).replace("\"", "'").replace("\\", "/");
            return "{\"_error\":\"not serializable: " + (msg.length() > 200 ? msg.substring(0, 200) : msg) + "\"}";
        }
    }
}
