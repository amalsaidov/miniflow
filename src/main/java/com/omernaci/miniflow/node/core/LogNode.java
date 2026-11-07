package com.omernaci.miniflow.node.core;

import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LogNode implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger("miniflow.log-node");

    @Override
    public String type() {
        return "log";
    }

    @Override
    public String label() {
        return "Log";
    }

    @Override
    public String description() {
        return "Writes a message to the application log and passes data through.";
    }

    @Override
    public Map<String, Object> defaultParams() {
        return Map.of("message", "Received: {{ input }}");
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.textarea("message").mandatory());
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        String message = String.valueOf(params.getOrDefault("message", input));
        log.info(message);
        Map<String, Object> out = new LinkedHashMap<>(input == null ? Map.of() : input);
        out.put("logged", message);
        return NodeResult.of(out);
    }
}
