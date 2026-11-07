package com.omernaci.miniflow.node.core;

import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SetNode implements NodeExecutor {

    @Override
    public String type() {
        return "set";
    }

    @Override
    public String label() {
        return "Set fields";
    }

    @Override
    public String description() {
        return "Adds new fields to the data or changes existing ones.";
    }

    @Override
    public Map<String, Object> defaultParams() {
        return Map.of("keepInput", true, "values", Map.of("newField", "{{ input }}"));
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.keyValue("values"),
                ParamField.bool("keepInput"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        boolean keepInput = !Boolean.FALSE.equals(params.get("keepInput"));
        Map<String, Object> out = new LinkedHashMap<>();
        if (keepInput && input != null) {
            out.putAll(input);
        }
        Object values = params.get("values");
        if (values instanceof Map<?, ?> map) {
            out.putAll((Map<String, Object>) map);
        }
        return NodeResult.of(out);
    }
}
