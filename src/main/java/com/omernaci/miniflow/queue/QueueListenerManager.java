package com.omernaci.miniflow.queue;

import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.execution.ExecutionService;
import com.omernaci.miniflow.workflow.Workflow;
import com.omernaci.miniflow.workflow.WorkflowChangedEvent;
import com.omernaci.miniflow.workflow.WorkflowService;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.CloseOptions;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
public class QueueListenerManager {

    private static final Logger log = LoggerFactory.getLogger(QueueListenerManager.class);

    private final WorkflowService workflows;
    private final ExecutionService executions;
    private final CredentialService credentials;
    private final QueueSupport queues;

    private final Map<String, Running> running = new ConcurrentHashMap<>();
    private final Map<String, String> errors = new ConcurrentHashMap<>();

    private record Running(String signature, String summary, AutoCloseable handle) {
    }

    public QueueListenerManager(WorkflowService workflows, ExecutionService executions,
                                CredentialService credentials, QueueSupport queues) {
        this.workflows = workflows;
        this.executions = executions;
        this.credentials = credentials;
        this.queues = queues;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAll() {
        workflows.active().forEach(this::sync);
        if (!running.isEmpty()) {
            log.info("Kuyruk dinleyicileri: {}", running.keySet());
        }
    }

    @EventListener
    public void onChange(WorkflowChangedEvent event) {
        Optional<Workflow> wf = workflows.find(event.workflowId());
        if (wf.isPresent()) {
            sync(wf.get());
        } else {
            stop(event.workflowId());
        }
    }

    public Optional<String> summary(String workflowId) {
        Running r = running.get(workflowId);
        return r == null ? Optional.empty() : Optional.of(r.summary());
    }

    public Optional<String> error(String workflowId) {
        return Optional.ofNullable(errors.get(workflowId));
    }

    private synchronized void sync(Workflow wf) {
        Optional<Map<String, Object>> cfg = wf.active() ? wf.listenConfig() : Optional.empty();
        String signature = cfg.map(Object::toString).orElse("");
        Running current = running.get(wf.id());
        if (current != null && current.signature().equals(signature)) {
            return;
        }
        stop(wf.id());
        if (cfg.isEmpty()) {
            return;
        }
        Map<String, Object> c = cfg.get();
        String type = String.valueOf(c.get("type")).trim().toLowerCase();
        String workflowId = wf.id();
        Consumer<Map<String, Object>> handler = data -> fire(workflowId, data);
        try {
            Map<String, String> cred = credentials.require(str(c, "credential"), type);
            Running r = switch (type) {
                case "rabbitmq" -> new Running(signature, "rabbitmq · " + str(c, "queue"),
                        rabbit(workflowId, cred, required(c, "queue"), handler));
                case "kafka" -> new Running(signature, "kafka · " + str(c, "topic"),
                        kafka(workflowId, cred, required(c, "topic"),
                                str(c, "group").isBlank() ? "miniflow-" + workflowId : str(c, "group"),
                                Boolean.parseBoolean(str(c, "fromBeginning")), handler));
                case "redis" -> str(c, "list").isBlank()
                        ? new Running(signature, "redis · " + str(c, "channel"),
                        redisPubSub(workflowId, cred, required(c, "channel"), handler))
                        : new Running(signature, "redis list · " + str(c, "list"),
                        redisList(workflowId, cred, str(c, "list"), handler));
                default -> throw new IllegalArgumentException("listen.type: rabbitmq | kafka | redis");
            };
            running.put(workflowId, r);
            errors.remove(workflowId);
            log.info("Workflow '{}' is listening: {}", workflowId, r.summary());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            errors.put(workflowId, msg);
            log.warn("Could not start queue listener for workflow '{}': {}", workflowId, msg);
        }
    }

    private synchronized void stop(String workflowId) {
        errors.remove(workflowId);
        Running r = running.remove(workflowId);
        if (r != null) {
            try {
                r.handle().close();
            } catch (Exception e) {
                log.debug("Error while closing listener: {}", e.getMessage());
            }
            log.info("Listener of workflow '{}' stopped", workflowId);
        }
    }

    private void fire(String workflowId, Map<String, Object> data) {
        try {
            workflows.find(workflowId).filter(Workflow::active)
                    .ifPresent(wf -> executions.run(wf, data, "queue"));
        } catch (Exception e) {
            log.error("Workflow '{}' could not be run for a queue message", workflowId, e);
        }
    }

    @PreDestroy
    public void stopAll() {
        List.copyOf(running.keySet()).forEach(this::stop);
    }

    private AutoCloseable rabbit(String workflowId, Map<String, String> cred, String queue,
                                 Consumer<Map<String, Object>> handler) throws Exception {
        Connection conn = queues.rabbit(cred).newConnection("miniflow-" + workflowId);
        try {
            try (Channel probe = conn.createChannel()) {
                probe.queueDeclarePassive(queue);
            } catch (Exception missing) {
                try (Channel decl = conn.createChannel()) {
                    decl.queueDeclare(queue, true, false, false, null);
                }
            }
            Channel ch = conn.createChannel();
            ch.basicQos(1);
            ch.basicConsume(queue, false, (tag, delivery) -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("body", queues.decode(new String(delivery.getBody(), StandardCharsets.UTF_8)));
                data.put("queue", queue);
                data.put("routingKey", delivery.getEnvelope().getRoutingKey());
                data.put("exchange", delivery.getEnvelope().getExchange());
                data.put("messageId", delivery.getProperties().getMessageId());
                Map<String, Object> headers = new LinkedHashMap<>();
                if (delivery.getProperties().getHeaders() != null) {
                    delivery.getProperties().getHeaders().forEach((k, v) -> headers.put(k, String.valueOf(v)));
                }
                data.put("headers", headers);
                try {
                    handler.accept(data);
                } finally {
                    ch.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                }
            }, tag -> { });
            return conn::close;
        } catch (Exception e) {
            conn.abort();
            throw e;
        }
    }

    private AutoCloseable kafka(String workflowId, Map<String, String> cred, String topic, String group,
                                boolean fromBeginning, Consumer<Map<String, Object>> handler) {
        Properties p = queues.kafka(cred);
        p.put("group.id", group);
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", StringDeserializer.class.getName());
        p.put("enable.auto.commit", "false");
        p.put("auto.offset.reset", fromBeginning ? "earliest" : "latest");
        p.put("max.poll.records", "10");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(p);
        AtomicBoolean active = new AtomicBoolean(true);

        Thread t = new Thread(() -> {
            try {
                consumer.subscribe(List.of(topic));
                while (active.get()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                    for (ConsumerRecord<String, String> r : records) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("body", queues.decode(r.value()));
                        data.put("key", r.key());
                        data.put("topic", r.topic());
                        data.put("partition", r.partition());
                        data.put("offset", r.offset());
                        data.put("timestamp", r.timestamp());
                        handler.accept(data);
                    }
                    if (!records.isEmpty()) {
                        consumer.commitSync();
                    }
                }
            } catch (WakeupException ignored) {
            } catch (Exception e) {
                errors.put(workflowId, String.valueOf(e.getMessage()));
                log.warn("Kafka dinleyici '{}' durdu: {}", workflowId, e.getMessage());
            } finally {
                consumer.close(CloseOptions.timeout(Duration.ofSeconds(5)));
            }
        }, "miniflow-kafka-" + workflowId);
        t.setDaemon(true);
        t.start();

        return () -> {
            active.set(false);
            consumer.wakeup();
            t.join(10_000);
        };
    }

    private AutoCloseable redisPubSub(String workflowId, Map<String, String> cred, String channel,
                                      Consumer<Map<String, Object>> handler) {
        RedisClient client = queues.redis(cred);
        StatefulRedisPubSubConnection<String, String> conn = client.connectPubSub();
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "miniflow-redis-" + workflowId);
            t.setDaemon(true);
            return t;
        });
        conn.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String ch, String message) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("body", queues.decode(message));
                data.put("channel", ch);
                worker.submit(() -> handler.accept(data));
            }
        });
        conn.sync().subscribe(channel);
        return () -> {
            worker.shutdownNow();
            conn.close();
            client.shutdown();
        };
    }

    private AutoCloseable redisList(String workflowId, Map<String, String> cred, String key,
                                    Consumer<Map<String, Object>> handler) {
        RedisClient client = queues.redis(cred);
        StatefulRedisConnection<String, String> conn = client.connect();
        AtomicBoolean active = new AtomicBoolean(true);
        Thread t = new Thread(() -> {
            while (active.get()) {
                try {
                    KeyValue<String, String> kv = conn.sync().blpop(5, key);
                    if (kv != null && kv.hasValue()) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("body", queues.decode(kv.getValue()));
                        data.put("list", key);
                        handler.accept(data);
                    }
                } catch (Exception e) {
                    if (!active.get()) {
                        break;
                    }
                    errors.put(workflowId, String.valueOf(e.getMessage()));
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }, "miniflow-redis-list-" + workflowId);
        t.setDaemon(true);
        t.start();
        return () -> {
            active.set(false);
            t.interrupt();
            conn.close();
            client.shutdown();
        };
    }

    private static String str(Map<String, Object> c, String key) {
        Object v = c.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String required(Map<String, Object> c, String key) {
        String v = str(c, key);
        if (v.isEmpty()) {
            throw new IllegalArgumentException("listen." + key + " is required");
        }
        return v;
    }
}
