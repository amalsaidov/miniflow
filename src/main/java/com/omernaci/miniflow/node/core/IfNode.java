package com.omernaci.miniflow.node.core;

import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class IfNode implements NodeExecutor {

    @Override
    public String type() {
        return "if";
    }

    @Override
    public String label() {
        return "If";
    }

    @Override
    public String description() {
        return "Continues from true if the condition holds, otherwise from false.";
    }

    @Override
    public Map<String, Object> defaultParams() {
        return Map.of("condition", "{{ input.amount > 100 }}");
    }

    @Override
    public List<String> outputs() {
        return List.of("true", "false");
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.text("condition").mandatory().placeholder("{{ input.total > 1000 }}"));
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        Object condition = params.get("condition");
        boolean result = condition instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(condition));
        return NodeResult.branch(input, result ? "true" : "false");
    }
}
