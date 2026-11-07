package com.omernaci.miniflow.queue;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.rabbitmq.client.ConnectionFactory;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

@Component
public class QueueSupport {

    private volatile ClientResources redisResources;
    private final ObjectMapper mapper;

    public QueueSupport(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String encode(Object message) {
        if (message == null) {
            return "";
        }
        if (message instanceof CharSequence cs) {
            return cs.toString();
        }
        try {
            return mapper.writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(message);
        } catch (JacksonException e) {
            throw new MiniFlowException("error.queue.encode", e.getOriginalMessage());
        }
    }

    public Object decode(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            try {
                return mapper.readValue(t, Object.class);
            } catch (Exception ignored) {
            }
        }
        return text;
    }

    public RedisClient redis(Map<String, String> c) {
        RedisURI.Builder b = RedisURI.Builder.redis(c.getOrDefault("host", "localhost"), intOf(c, "port", 6379))
                .withDatabase(intOf(c, "database", 0))
                .withSsl(Boolean.parseBoolean(c.getOrDefault("ssl", "false")))
                .withTimeout(Duration.ofSeconds(10));
        String password = c.get("password");
        String username = c.get("username");
        if (password != null && !password.isBlank()) {
            if (username != null && !username.isBlank()) {
                b.withAuthentication(username, password);
            } else {
                b.withPassword(password.toCharArray());
            }
        }
        return RedisClient.create(redisResources(), b.build());
    }

    public ConnectionFactory rabbit(Map<String, String> c) {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost(c.getOrDefault("host", "localhost"));
        f.setPort(intOf(c, "port", 5672));
        f.setUsername(c.getOrDefault("username", "guest"));
        f.setPassword(c.getOrDefault("password", ""));
        f.setVirtualHost(c.getOrDefault("virtualHost", "/"));
        f.setConnectionTimeout(10_000);
        f.setAutomaticRecoveryEnabled(true);
        if (Boolean.parseBoolean(c.getOrDefault("ssl", "false"))) {
            try {
                f.useSslProtocol(SSLContext.getDefault());
                f.enableHostnameVerification();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        return f;
    }

    public Properties kafka(Map<String, String> c) {
        Properties p = new Properties();
        p.put("bootstrap.servers", c.getOrDefault("bootstrapServers", "localhost:9092"));
        String protocol = c.getOrDefault("securityProtocol", "PLAINTEXT");
        p.put("security.protocol", protocol);
        if (protocol.startsWith("SASL")) {
            String mechanism = c.getOrDefault("saslMechanism", "PLAIN");
            String module = mechanism.startsWith("SCRAM")
                    ? "org.apache.kafka.common.security.scram.ScramLoginModule"
                    : "org.apache.kafka.common.security.plain.PlainLoginModule";
            p.put("sasl.mechanism", mechanism);
            p.put("sasl.jaas.config", module + " required username=\"" + jaasEscape(c.getOrDefault("username", ""))
                    + "\" password=\"" + jaasEscape(c.getOrDefault("password", "")) + "\";");
        }
        p.put("client.id", "miniflow");
        p.put("request.timeout.ms", "10000");
        return p;
    }

    private static String jaasEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static int intOf(Map<String, String> c, String key, int defaultValue) {
        String v = c.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new MiniFlowException("error.param.number", key, v);
        }
    }

    private ClientResources redisResources() {
        ClientResources r = redisResources;
        if (r == null) {
            synchronized (this) {
                r = redisResources;
                if (r == null) {
                    r = DefaultClientResources.create();
                    redisResources = r;
                }
            }
        }
        return r;
    }

    @PreDestroy
    public void shutdown() {
        if (redisResources != null) {
            redisResources.shutdown();
        }
    }
}