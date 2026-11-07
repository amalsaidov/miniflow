package com.omernaci.miniflow.engine;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.engine.expression.ExpressionResolver;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.NodeTypeInfo;
import com.omernaci.miniflow.workflow.Workflow;
import com.omernaci.miniflow.workflow.Workflow.NodeDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final int MAX_STEPS = 1000;

    private final Map<String, NodeExecutor> executors;
    private final ExpressionResolver expressions;

    public WorkflowEngine(List<NodeExecutor> executorList, ExpressionResolver expressions) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(NodeExecutor::type, Function.identity()));
        this.expressions = expressions;
        log.info("Registered node types: {}", executors.keySet());
    }

    private static final List<String> PALETTE_ORDER = List.of("trigger", "set", "if", "dedupe", "wait", "http", "ai",
            "email", "slack", "github", "googleSheets", "database", "spreadsheet", "rss",
            "redis", "rabbitmq", "kafka", "log");

    public List<NodeTypeInfo> nodeTypes() {
        return executors.values().stream()
                .sorted(Comparator.comparingInt((NodeExecutor e) -> {
                    int i = PALETTE_ORDER.indexOf(e.type());
                    return i < 0 ? Integer.MAX_VALUE : i;
                }).thenComparing(NodeExecutor::type))
                .map(NodeTypeInfo::from)
                .toList();
    }

    public ExecutionContext run(Workflow workflow, Map<String, Object> triggerData) {
        return run(workflow, triggerData, "manual");
    }

    public ExecutionContext run(Workflow workflow, Map<String, Object> triggerData, String mode) {
        ExecutionContext ctx = new ExecutionContext(workflow.id(), mode, triggerData);
        Deque<Pending> queue = new ArrayDeque<>();
        queue.add(new Pending(workflow.startNode().id(), ctx.trigger()));

        int stepCount = 0;
        while (!queue.isEmpty()) {
            if (++stepCount > MAX_STEPS) {
                throw new MiniFlowException("error.engine.maxSteps", MAX_STEPS);
            }
            Pending current = queue.poll();
            NodeDef node = workflow.node(current.nodeId())
                    .orElseThrow(() -> new MiniFlowException("error.engine.nodeNotFound", current.nodeId()));

            NodeResult result = executeNode(node, current.input(), ctx);
            if (result == null) {
                continue;
            }
            for (String next : workflow.nextNodes(node.id(), result.branch())) {
                queue.add(new Pending(next, result.output()));
            }
        }
        return ctx;
    }

    private NodeResult executeNode(NodeDef node, Map<String, Object> input, ExecutionContext ctx) {
        long start = System.currentTimeMillis();
        NodeExecutor executor = executors.get(node.type());
        try {
            if (executor == null) {
                throw new MiniFlowException("error.engine.unknownNodeType", node.type());
            }
            Map<String, Object> params = expressions.resolve(node.params(), input, ctx);
            NodeResult result = executor.execute(params, input, ctx);
            ctx.record(new ExecutionContext.StepLog(node.id(), node.type(), "SUCCESS", result.branch(),
                    result.output(), null, System.currentTimeMillis() - start));
            return result;
        } catch (Exception e) {
            log.warn("Node '{}' failed: {}", node.id(), e.getMessage());
            ctx.record(new ExecutionContext.StepLog(node.id(), node.type(), "ERROR", null,
                    null, e.getMessage(), System.currentTimeMillis() - start));
            return null;
        }
    }

    private record Pending(String nodeId, Map<String, Object> input) {
    }
}
