package com.omernaci.miniflow.node.notify;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.support.HttpSupport;
import com.omernaci.miniflow.node.support.Params;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class ChatWebhookNode implements NodeExecutor {

    private final CredentialService credentials;
    private final RestClient client;

    ChatWebhookNode(CredentialService credentials, RestClient.Builder builder) {
        this.credentials = credentials;
        this.client = HttpSupport.client(builder, Duration.ofSeconds(30));
    }

    protected abstract Map<String, Object> body(String text, Map<String, Object> params);

    @Override
    public List<String> credentialTypes() {
        return List.of(type());
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("credential", "");
        p.put("text", "{{ input.text }}");
        return p;
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        Map<String, String> c = credentials.require(Params.str(params, "credential"), type());
        String text = Params.required(params, "text");
        HttpSupport.Response r = HttpSupport.postJson(client, c.get("webhookUrl"), body(text, params), h -> { });
        if (!r.ok()) {
            throw new MiniFlowException("error.chat", type(), r.status(), r.shortBody());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sent", true);
        out.put("status", r.status());
        return NodeResult.of(out);
    }
}
