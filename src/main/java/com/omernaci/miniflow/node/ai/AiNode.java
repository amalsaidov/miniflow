package com.omernaci.miniflow.node.ai;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import com.omernaci.miniflow.node.support.HttpSupport;
import com.omernaci.miniflow.node.support.Params;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiNode implements NodeExecutor {

    private final CredentialService credentials;
    private final RestClient client;
    private final ObjectMapper mapper;

    public AiNode(CredentialService credentials, RestClient.Builder builder, ObjectMapper mapper) {
        this.credentials = credentials;
        this.client = HttpSupport.client(builder, Duration.ofSeconds(120));
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "ai";
    }

    @Override
    public String label() {
        return "AI";
    }

    @Override
    public List<String> credentialTypes() {
        return List.of("ai");
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("credential", "");
        p.put("system", "Answer briefly and clearly.");
        p.put("prompt", "Summarize this text in one sentence: {{ input.text }}");
        p.put("json", false);
        return p;
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.credential(),
                ParamField.textarea("system"),
                ParamField.textarea("prompt").mandatory(),
                ParamField.bool("json"),
                ParamField.number("maxTokens"),
                ParamField.number("temperature"),
                ParamField.select("thinking", "low", "medium", "high"));
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        Map<String, String> c = credentials.require(Params.str(params, "credential"), "ai");
        String provider = c.getOrDefault("provider", "openai").trim().toLowerCase();
        String model = c.get("model");
        String prompt = Params.required(params, "prompt");
        String system = Params.str(params, "system");

        Reply reply = switch (provider) {
            case "anthropic" -> anthropic(c, model, system, prompt, params);
            case "gemini" -> gemini(c, model, system, prompt, params);
            default -> openai(c, model, system, prompt, params);
        };

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("text", reply.text());
        if (Params.bool(params, "json", false)) {
            out.put("data", parseJson(reply.text()));
        }
        out.put("model", model);
        out.put("provider", provider);
        out.put("usage", reply.usage());
        return NodeResult.of(out);
    }

    private record Reply(String text, Object usage) {
    }

    @SuppressWarnings("unchecked")
    private Reply openai(Map<String, String> c, String model, String system, String prompt, Map<String, Object> params) {
        String configured = c.getOrDefault("baseUrl", "").trim();
        String base = configured.isEmpty() ? "https://api.openai.com/v1" : configured.replaceAll("/+$", "");

        List<Map<String, Object>> messages = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            messages.add(Map.of("role", "system", "content", system));
        }
        messages.add(Map.of("role", "user", "content", prompt));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        Object maxTokens = params.get("maxTokens");
        if (maxTokens != null && !String.valueOf(maxTokens).isBlank()) {
            body.put(configured.isEmpty() ? "max_completion_tokens" : "max_tokens", Params.integer(params, "maxTokens", 1024));
        }
        Double temperature = Params.decimal(params, "temperature");
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        String apiKey = c.getOrDefault("apiKey", "");
        HttpSupport.Response r = HttpSupport.postJson(client, base + "/chat/completions", body, h -> {
            if (!apiKey.isBlank()) {
                h.setBearerAuth(apiKey);
            }
        });
        Map<String, Object> json = r.json(mapper);
        if (!r.ok()) {
            throw new MiniFlowException("error.ai", r.status(), errorMessage(json, r));
        }
        try {
            List<Object> choices = (List<Object>) json.get("choices");
            Map<String, Object> message = (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("message");
            return new Reply(String.valueOf(message.get("content")), json.get("usage"));
        } catch (RuntimeException e) {
            throw new MiniFlowException("error.ai", r.status(), r.shortBody());
        }
    }

    @SuppressWarnings("unchecked")
    private Reply anthropic(Map<String, String> c, String model, String system, String prompt, Map<String, Object> params) {
        String configured = c.getOrDefault("baseUrl", "").trim();
        String base = configured.isEmpty() ? "https://api.anthropic.com" : configured.replaceAll("/+$", "");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", Params.integer(params, "maxTokens", 1024));
        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        Double temperature = Params.decimal(params, "temperature");
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        String apiKey = c.getOrDefault("apiKey", "");
        HttpSupport.Response r = HttpSupport.postJson(client, base + "/v1/messages", body, h -> {
            h.set("x-api-key", apiKey);
            h.set("anthropic-version", "2023-06-01");
        });
        Map<String, Object> json = r.json(mapper);
        if (!r.ok()) {
            throw new MiniFlowException("error.ai", r.status(), errorMessage(json, r));
        }
        StringBuilder text = new StringBuilder();
        if (json.get("content") instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> m && "text".equals(m.get("type"))) {
                    text.append(m.get("text"));
                }
            }
        }
        return new Reply(text.toString(), json.get("usage"));
    }

    @SuppressWarnings("unchecked")
    private Reply gemini(Map<String, String> c, String model, String system, String prompt, Map<String, Object> params) {
        String configured = c.getOrDefault("baseUrl", "").trim();
        String base = configured.isEmpty() ? "https://generativelanguage.googleapis.com" : configured.replaceAll("/+$", "");
        String name = model == null ? "" : model.trim().replaceFirst("^models/", "");
        if (!name.matches("[A-Za-z0-9._-]+")) {
            throw new MiniFlowException("error.param.required", "model");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", name);
        if (system != null && !system.isBlank()) {
            body.put("system_instruction", system);
        }
        body.put("input", prompt);
        Map<String, Object> config = new LinkedHashMap<>();
        Object maxTokens = params.get("maxTokens");
        if (maxTokens != null && !String.valueOf(maxTokens).isBlank()) {
            config.put("max_output_tokens", Params.integer(params, "maxTokens", 1024));
        }
        Double temperature = Params.decimal(params, "temperature");
        if (temperature != null) {
            config.put("temperature", temperature);
        }
        String thinking = Params.str(params, "thinking");
        if (thinking != null && !thinking.isBlank()) {
            config.put("thinking_level", thinking.trim());
        }
        if (!config.isEmpty()) {
            body.put("generation_config", config);
        }

        String apiKey = c.getOrDefault("apiKey", "");
        HttpSupport.Response r = HttpSupport.postJson(client, base + "/v1beta/interactions", body,
                h -> h.set("x-goog-api-key", apiKey));
        Map<String, Object> json = r.json(mapper);
        if (!r.ok()) {
            throw new MiniFlowException("error.ai", r.status(), errorMessage(json, r));
        }

        StringBuilder text = new StringBuilder();
        if (json.get("steps") instanceof List<?> steps) {
            for (Object s : steps) {
                if (s instanceof Map<?, ?> step && "model_output".equals(step.get("type"))
                        && step.get("content") instanceof List<?> content) {
                    for (Object part : content) {
                        if (part instanceof Map<?, ?> p && "text".equals(p.get("type")) && p.get("text") != null) {
                            text.append(p.get("text"));
                        }
                    }
                }
            }
        }
        if (text.isEmpty() && json.get("output_text") != null) {
            text.append(json.get("output_text"));
        }
        String status = json.get("status") == null ? null : String.valueOf(json.get("status"));
        if (text.isEmpty() || "failed".equals(status)) {
            String reason;
            if (json.get("errors") instanceof List<?> errors && !errors.isEmpty()
                    && errors.get(0) instanceof Map<?, ?> e && e.get("message") != null) {
                reason = String.valueOf(e.get("message"));
            } else if ("incomplete".equals(status)) {
                reason = "empty answer (incomplete) - increase maxTokens or set \"thinking\": \"low\"";
            } else {
                reason = "empty answer" + (status == null ? "" : " (" + status + ")");
            }
            throw new MiniFlowException("error.ai", r.status(), reason);
        }
        return new Reply(text.toString(), json.get("usage"));
    }

    @SuppressWarnings("unchecked")
    private static String errorMessage(Map<String, Object> json, HttpSupport.Response r) {
        if (json.get("error") instanceof Map<?, ?> err && err.get("message") != null) {
            return String.valueOf(((Map<String, Object>) err).get("message"));
        }
        return r.shortBody();
    }

    private Object parseJson(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```\\s*$", "");
        }
        try {
            return mapper.readValue(t, Object.class);
        } catch (Exception e) {
            throw new MiniFlowException("error.ai.json", t.length() > 200 ? t.substring(0, 200) + "…" : t);
        }
    }
}
