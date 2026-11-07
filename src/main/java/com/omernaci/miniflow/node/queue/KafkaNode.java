package com.omernaci.miniflow.node.queue;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import com.omernaci.miniflow.node.support.Params;
import com.omernaci.miniflow.queue.QueueSupport;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaNode implements NodeExecutor {

    private final CredentialService credentials;
    private final QueueSupport queues;

    public KafkaNode(CredentialService credentials, QueueSupport queues) {
        this.credentials = credentials;
        this.queues = queues;
    }

    @Override
    public String type() {
        return "kafka";
    }

    @Override
    public String label() {
        return "Kafka";
    }

    @Override
    public List<String> credentialTypes() {
        return List.of("kafka");
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("credential", "");
        p.put("topic", "orders");
        p.put("key", "{{ input.id }}");
        p.put("message", "{{ input }}");
        return p;
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.credential(),
                ParamField.text("topic").mandatory().placeholder("orders"),
                ParamField.text("key").placeholder("{{ input.id }}"),
                ParamField.json("message").mandatory());
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        Map<String, String> c = credentials.require(Params.str(params, "credential"), "kafka");
        String topic = Params.required(params, "topic");
        String key = Params.str(params, "key");
        String value = queues.encode(params.get("message"));

        Properties p = queues.kafka(c);
        p.put("key.serializer", StringSerializer.class.getName());
        p.put("value.serializer", StringSerializer.class.getName());
        p.put("acks", "all");
        p.put("max.block.ms", "10000");
        p.put("delivery.timeout.ms", "15000");
        p.put("linger.ms", "0");

        KafkaProducer<String, String> producer = null;
        try {
            producer = new KafkaProducer<>(p);
            RecordMetadata md = producer.send(new ProducerRecord<>(topic, key == null || key.isBlank() ? null : key, value))
                    .get(20, TimeUnit.SECONDS);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sent", true);
            out.put("topic", md.topic());
            out.put("partition", md.partition());
            out.put("offset", md.offset());
            return NodeResult.of(out);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new MiniFlowException(cause, "error.kafka", String.valueOf(cause.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MiniFlowException(e, "error.kafka", "interrupted");
        } catch (Exception e) {
            throw new MiniFlowException(e, "error.kafka", String.valueOf(e.getMessage()));
        } finally {
            if (producer != null) {
                producer.close(Duration.ofSeconds(5));
            }
        }
    }
}
