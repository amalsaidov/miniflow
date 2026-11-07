package com.omernaci.miniflow.node.core;

import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TriggerNode implements NodeExecutor {

    @Override
    public String type() {
        return "trigger";
    }

    @Override
    public String label() {
        return "Trigger";
    }

    @Override
    public String description() {
        return "Starts the workflow: path → /webhook/{path}, cron → scheduled (when active).";
    }

    @Override
    public Map<String, Object> defaultParams() {
        return Map.of("path", "", "cron", "");
    }

    @Override
    public boolean hasInput() {
        return false;
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.text("path").placeholder("orders"),
                ParamField.text("cron").placeholder("*/5 * * * *"),
                ParamField.json("listen"),
                ParamField.text("signatureSecretVar").placeholder("githubWebhookSecret"),
                ParamField.json("sample"));
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        return NodeResult.of(ctx.trigger());
    }
}
