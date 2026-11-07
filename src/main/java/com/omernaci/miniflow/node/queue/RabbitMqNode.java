package com.omernaci.miniflow.node.queue;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import com.omernaci.miniflow.node.support.Params;
import com.omernaci.miniflow.queue.QueueSupport;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RabbitMqNode implements NodeExecutor {

    private final CredentialService credentials;
    private final QueueSupport queues;

    public RabbitMqNode(CredentialService credentials, QueueSupport queues) {
        this.credentials = credentials;
        this.queues = queues;
    }

    @Override
    public String type() {
        return "rabbitmq";
    }

    @Override
    public String label() {
        return "RabbitMQ";
    }

    @Override
    public List<String> credentialTypes() {
        return List.of("rabbitmq");
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("credential", "");
        p.put("exchange", "");
        p.put("routingKey", "orders");
        p.put("message", "{{ input }}");
        p.put("declareQueue", true);
        return p;
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.credential(),
                ParamField.text("exchange"),
                ParamField.text("routingKey").mandatory().placeholder("orders"),
                ParamField.json("message").mandatory(),
                ParamField.bool("declareQueue"),
                ParamField.bool("persistent"));
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        Map<String, String> c = credentials.require(Params.str(params, "credential"), "rabbitmq");
        String exchange = Params.str(params, "exchange", "");
        String routingKey = Params.required(params, "routingKey");
        Object message = params.get("message");
        String body = queues.encode(message);
        String messageId = UUID.randomUUID().toString();

        try (Connection conn = queues.rabbit(c).newConnection("miniflow-publisher");
             Channel ch = conn.createChannel()) {
            if (exchange.isEmpty() && Params.bool(params, "declareQueue", false)) {
                ch.queueDeclare(routingKey, true, false, false, null);
            }
            AtomicBoolean returned = new AtomicBoolean(false);
            ch.addReturnListener(r -> returned.set(true));
            ch.confirmSelect();
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType(message instanceof CharSequence ? "text/plain" : "application/json")
                    .contentEncoding("UTF-8")
                    .deliveryMode(Params.bool(params, "persistent", true) ? 2 : 1)
                    .messageId(messageId)
                    .build();
            ch.basicPublish(exchange, routingKey, true, props, body.getBytes(StandardCharsets.UTF_8));
            ch.waitForConfirmsOrDie(10_000);
            if (returned.get()) {
                throw new MiniFlowException("error.rabbitmq.unroutable", exchange.isEmpty() ? "(default)" : exchange, routingKey);
            }
        } catch (MiniFlowException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new MiniFlowException(e, "error.rabbitmq", String.valueOf(e.getMessage()));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sent", true);
        out.put("exchange", exchange);
        out.put("routingKey", routingKey);
        out.put("messageId", messageId);
        return NodeResult.of(out);
    }
}
