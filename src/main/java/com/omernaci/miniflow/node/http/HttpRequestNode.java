package com.omernaci.miniflow.node.http;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import com.omernaci.miniflow.node.support.HttpSupport;
import com.omernaci.miniflow.node.support.Params;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HttpRequestNode implements NodeExecutor {

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final CredentialService credentials;

    public HttpRequestNode(RestClient.Builder builder, ObjectMapper mapper, CredentialService credentials) {
        this.restClient = HttpSupport.client(builder, Duration.ofSeconds(30));
        this.mapper = mapper;
        this.credentials = credentials;
    }

    @Override
    public String type() {
        return "http";
    }

    @Override
    public String label() {
        return "HTTP Request";
    }

    @Override
    public String description() {
        return "Calls an external service; output: status, ok and body.";
    }

    @Override
    public List<String> credentialTypes() {
        return List.of("http-header");
    }

    @Override
    public Map<String, Object> defaultParams() {
        return Map.of("method", "GET", "url", "https://httpbin.org/get", "failOnError", true);
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.select("method", "GET", "POST", "PUT", "PATCH", "DELETE").mandatory(),
                ParamField.text("url").mandatory().placeholder("https://api.example.com/items"),
                ParamField.keyValue("headers"),
                ParamField.json("body").when("method", "POST", "PUT", "PATCH", "DELETE"),
                ParamField.of("credential", "credential"),
                ParamField.bool("failOnError"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        Object urlParam = params.get("url");
        if (urlParam == null || String.valueOf(urlParam).isBlank()) {
            throw new MiniFlowException("error.param.required", "url");
        }
        String url = String.valueOf(urlParam);
        HttpMethod method = HttpMethod.valueOf(String.valueOf(params.getOrDefault("method", "GET")).toUpperCase());
        boolean failOnError = !Boolean.FALSE.equals(params.get("failOnError"));

        RestClient.RequestBodySpec request = restClient.method(method).uri(url);
        if (params.get("headers") instanceof Map<?, ?> headers) {
            ((Map<String, Object>) headers).forEach((k, v) -> {
                if (v != null && !String.valueOf(v).isBlank()) {
                    request.header(k, String.valueOf(v));
                }
            });
        }
        String credentialName = Params.str(params, "credential");
        if (credentialName != null && !credentialName.isBlank()) {
            Map<String, String> c = credentials.require(credentialName, "http-header");
            request.header(c.get("headerName"), c.get("headerValue"));
        }
        if (params.get("body") != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(params.get("body"));
        }

        RestClient.ResponseSpec spec = request.retrieve();
        if (!failOnError) {
            spec = spec.onStatus(HttpStatusCode::isError, (req, res) -> {  });
        }
        ResponseEntity<String> response;
        try {
            response = spec.toEntity(String.class);
        } catch (RestClientException e) {
            if (failOnError) {
                throw e;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", 0);
            out.put("ok", false);
            out.put("body", null);
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            out.put("error", root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage()));
            return NodeResult.of(out);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", response.getStatusCode().value());
        out.put("ok", response.getStatusCode().is2xxSuccessful());
        out.put("body", parseBody(response.getBody()));
        return NodeResult.of(out);
    }

    private Object parseBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            try {
                return mapper.readValue(t, Object.class);
            } catch (Exception ignored) {
            }
        }
        return raw.length() > 5000 ? raw.substring(0, 5000) + "…(truncated)" : raw;
    }
}
