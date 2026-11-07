package com.omernaci.miniflow.node.core;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
public class DedupeNode implements NodeExecutor {

    private static final int MAX_KEY_LENGTH = 200;

    private final SeenKeyRepository repository;

    public DedupeNode(SeenKeyRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return "dedupe";
    }

    @Override
    public String label() {
        return "Is new? (dedupe)";
    }

    @Override
    public String description() {
        return "Goes to new the first time a key is seen, to seen afterwards.";
    }

    @Override
    public Map<String, Object> defaultParams() {
        return Map.of("key", "{{ input.id }}");
    }

    @Override
    public List<String> outputs() {
        return List.of("new", "seen");
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.text("key").mandatory().placeholder("{{ input.id }}"));
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        Object raw = params.get("key");
        if (raw == null || String.valueOf(raw).isBlank() || "null".equals(String.valueOf(raw))) {
            throw new MiniFlowException("error.param.required", "key");
        }
        String workflowId = ctx.workflowId() == null ? "-" : ctx.workflowId();
        String key = normalize(String.valueOf(raw));

        boolean seen = repository.existsByWorkflowIdAndKeyValue(workflowId, key);
        if (!seen && !ctx.isManual()) {
            try {
                repository.save(new SeenKeyEntity(workflowId, key));
            } catch (DataIntegrityViolationException e) {
                seen = true;
            }
        }
        return NodeResult.branch(input, seen ? "seen" : "new");
    }

    private static String normalize(String key) {
        if (key.length() <= MAX_KEY_LENGTH) {
            return key;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
