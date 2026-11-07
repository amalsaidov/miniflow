package com.omernaci.miniflow.node.core;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import com.omernaci.miniflow.node.support.Params;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WaitNode implements NodeExecutor {

    static final int MAX_SECONDS = 300;

    @Override
    public String type() {
        return "wait";
    }

    @Override
    public String label() {
        return "Wait";
    }

    @Override
    public Map<String, Object> defaultParams() {
        return Map.of("seconds", 5);
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.number("seconds").mandatory());
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        int seconds = Params.integer(params, "seconds", 5);
        if (seconds < 0 || seconds > MAX_SECONDS) {
            throw new MiniFlowException("error.wait.range", MAX_SECONDS);
        }
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MiniFlowException("error.wait.interrupted");
        }
        return NodeResult.of(input);
    }
}
