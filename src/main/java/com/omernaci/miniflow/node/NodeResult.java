package com.omernaci.miniflow.node;

import java.util.Map;

public record NodeResult(Map<String, Object> output, String branch) {

    public static NodeResult of(Map<String, Object> output) {
        return new NodeResult(output, "main");
    }

    public static NodeResult branch(Map<String, Object> output, String branch) {
        return new NodeResult(output, branch);
    }
}
