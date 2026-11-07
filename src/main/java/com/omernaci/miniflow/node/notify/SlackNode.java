package com.omernaci.miniflow.node.notify;

import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.node.ParamField;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SlackNode extends ChatWebhookNode {

    public SlackNode(CredentialService credentials, RestClient.Builder builder) {
        super(credentials, builder);
    }

    @Override
    public String type() {
        return "slack";
    }

    @Override
    public String label() {
        return "Slack";
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.credential(),
                ParamField.textarea("text").mandatory(),
                ParamField.of("blocks", "slackBlocks"));
    }

    @Override
    protected Map<String, Object> body(String text, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        if (params.get("blocks") instanceof List<?> blocks && !blocks.isEmpty()) {
            body.put("blocks", blocks);
        }
        return body;
    }
}
