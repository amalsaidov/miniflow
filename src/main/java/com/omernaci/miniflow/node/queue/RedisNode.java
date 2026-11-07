package com.omernaci.miniflow.node.queue;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import com.omernaci.miniflow.node.support.Params;
import com.omernaci.miniflow.queue.QueueSupport;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RedisNode implements NodeExecutor {

    private static final List<String> OPERATIONS = List.of("publish", "rpush", "lpush", "set", "get", "incr", "del");

    private final CredentialService credentials;
    private final QueueSupport queues;

    public RedisNode(CredentialService credentials, QueueSupport queues) {
        this.credentials = credentials;
        this.queues = queues;
    }

    @Override
    public String type() {
        return "redis";
    }

    @Override
    public String label() {
        return "Redis";
    }

    @Override
    public List<String> credentialTypes() {
        return List.of("redis");
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("credential", "");
        p.put("operation", "rpush");
        p.put("key", "jobs");
        p.put("message", "{{ input }}");
        return p;
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.credential(),
                ParamField.select("operation", "rpush", "lpush", "publish", "set", "get", "incr", "del").mandatory(),
                ParamField.text("key").mandatory().when("operation", "rpush", "lpush", "set", "get", "incr", "del"),
                ParamField.text("channel").mandatory().when("operation", "publish"),
                ParamField.json("message").when("operation", "rpush", "lpush", "publish", "set"),
                ParamField.number("ttlSeconds").when("operation", "set"));
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        String op = Params.str(params, "operation", "rpush").trim().toLowerCase();
        if (!OPERATIONS.contains(op)) {
            throw new MiniFlowException("error.param.oneOf", "operation", String.join(", ", OPERATIONS));
        }
        Map<String, String> c = credentials.require(Params.str(params, "credential"), "redis");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operation", op);

        RedisClient client = queues.redis(c);
        try (StatefulRedisConnection<String, String> conn = client.connect()) {
            RedisCommands<String, String> cmd = conn.sync();
            switch (op) {
                case "publish" -> {
                    String channel = Params.required(params, "channel");
                    out.put("channel", channel);
                    out.put("receivers", cmd.publish(channel, queues.encode(params.get("message"))));
                }
                case "rpush", "lpush" -> {
                    String key = Params.required(params, "key");
                    String msg = queues.encode(params.get("message"));
                    out.put("key", key);
                    out.put("length", "rpush".equals(op) ? cmd.rpush(key, msg) : cmd.lpush(key, msg));
                }
                case "set" -> {
                    String key = Params.required(params, "key");
                    String value = queues.encode(params.get("message"));
                    int ttl = Params.integer(params, "ttlSeconds", 0);
                    out.put("key", key);
                    out.put("result", ttl > 0 ? cmd.setex(key, ttl, value) : cmd.set(key, value));
                }
                case "get" -> {
                    String key = Params.required(params, "key");
                    out.put("key", key);
                    out.put("value", queues.decode(cmd.get(key)));
                }
                case "incr" -> {
                    String key = Params.required(params, "key");
                    out.put("key", key);
                    out.put("value", cmd.incr(key));
                }
                case "del" -> {
                    String key = Params.required(params, "key");
                    out.put("key", key);
                    out.put("deleted", cmd.del(key));
                }
                default -> throw new MiniFlowException("error.param.oneOf", "operation",
                        "publish, rpush, lpush, set, get, incr, del");
            }
        } catch (RedisException e) {
            throw new MiniFlowException(e, "error.redis", e.getMessage());
        } finally {
            client.shutdown();
        }
        return NodeResult.of(out);
    }
}
