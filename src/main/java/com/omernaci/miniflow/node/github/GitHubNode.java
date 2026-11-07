package com.omernaci.miniflow.node.github;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import com.omernaci.miniflow.node.support.HttpSupport;
import com.omernaci.miniflow.node.support.Params;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class GitHubNode implements NodeExecutor {

    private final CredentialService credentials;
    private final RestClient client;
    private final ObjectMapper mapper;

    public GitHubNode(CredentialService credentials, RestClient.Builder builder, ObjectMapper mapper) {
        this.credentials = credentials;
        this.client = HttpSupport.client(builder, Duration.ofSeconds(30));
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "github";
    }

    @Override
    public String label() {
        return "GitHub";
    }

    @Override
    public List<String> credentialTypes() {
        return List.of("github");
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("credential", "");
        p.put("operation", "createIssue");
        p.put("repo", "{{ vars.githubRepo }}");
        p.put("title", "Automated: {{ input.title }}");
        p.put("body", "{{ input }}");
        p.put("labels", List.of("miniflow"));
        return p;
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.credential(),
                ParamField.select("operation", "createIssue", "comment", "addLabels", "close", "createGist").mandatory(),
                ParamField.text("repo").mandatory().placeholder("owner/repo")
                        .when("operation", "createIssue", "comment", "addLabels", "close"),
                ParamField.text("issueNumber").mandatory().when("operation", "comment", "addLabels", "close"),
                ParamField.text("title").mandatory().when("operation", "createIssue"),
                ParamField.textarea("body").when("operation", "createIssue", "comment", "close"),
                ParamField.list("labels").when("operation", "createIssue", "addLabels"),
                ParamField.text("fileName").mandatory().placeholder("report.md").when("operation", "createGist"),
                ParamField.json("content").mandatory().when("operation", "createGist"),
                ParamField.text("description").when("operation", "createGist"),
                ParamField.bool("public").when("operation", "createGist"));
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        Map<String, String> c = credentials.require(Params.str(params, "credential"), "github");
        String api = c.getOrDefault("apiBase", "").isBlank() ? "https://api.github.com"
                : c.get("apiBase").replaceAll("/+$", "");
        String op = Params.str(params, "operation", "createIssue").trim();
        String repo = "createGist".equals(op) ? null : Params.required(params, "repo").trim();
        if (repo != null && !repo.matches("[\\w.-]+/[\\w.-]+")) {
            throw new MiniFlowException("error.github.repo", repo);
        }
        String base = api + "/repos/" + repo;
        Consumer<HttpHeaders> headers = h -> {
            h.setBearerAuth(c.get("token"));
            h.set("Accept", "application/vnd.github+json");
            h.set("X-GitHub-Api-Version", "2022-11-28");
            h.set("User-Agent", "MiniFlow");
        };

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operation", op);
        if (repo != null) {
            out.put("repo", repo);
        }
        switch (op) {
            case "createIssue" -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("title", Params.required(params, "title"));
                String text = Params.str(params, "body");
                if (text != null && !text.isBlank()) {
                    body.put("body", text);
                }
                List<String> labels = Params.stringList(params.get("labels"));
                if (!labels.isEmpty()) {
                    body.put("labels", labels);
                }
                Map<String, Object> json = call(HttpMethod.POST, base + "/issues", body, headers);
                out.put("number", json.get("number"));
                out.put("url", json.get("html_url"));
                out.put("id", json.get("id"));
            }
            case "comment" -> {
                Map<String, Object> json = call(HttpMethod.POST, base + "/issues/" + issue(params) + "/comments",
                        Map.of("body", Params.required(params, "body")), headers);
                out.put("id", json.get("id"));
                out.put("url", json.get("html_url"));
            }
            case "addLabels" -> {
                List<String> labels = Params.stringList(params.get("labels"));
                if (labels.isEmpty()) {
                    throw new MiniFlowException("error.param.required", "labels");
                }
                call(HttpMethod.POST, base + "/issues/" + issue(params) + "/labels", Map.of("labels", labels), headers);
                out.put("labels", labels);
            }
            case "close" -> {
                String number = issue(params);
                String text = Params.str(params, "body");
                if (text != null && !text.isBlank()) {
                    call(HttpMethod.POST, base + "/issues/" + number + "/comments", Map.of("body", text), headers);
                }
                Map<String, Object> json = call(HttpMethod.PATCH, base + "/issues/" + number,
                        Map.of("state", "closed"), headers);
                out.put("number", json.get("number"));
                out.put("state", json.get("state"));
            }
            case "createGist" -> {
                String fileName = Params.required(params, "fileName").trim();
                if (fileName.contains("/") || fileName.contains("\\")) {
                    throw new MiniFlowException("error.github.fileName", fileName);
                }
                String content = gistContent(params.get("content"), fileName);
                if (content.isBlank()) {
                    throw new MiniFlowException("error.param.required", "content");
                }
                Map<String, Object> body = new LinkedHashMap<>();
                String description = Params.str(params, "description");
                if (description != null && !description.isBlank()) {
                    body.put("description", description);
                }
                body.put("public", Params.bool(params, "public", false));
                body.put("files", Map.of(fileName, Map.of("content", content)));
                Map<String, Object> json = call(HttpMethod.POST, api + "/gists", body, headers);
                out.put("id", json.get("id"));
                out.put("url", json.get("html_url"));
                if (json.get("files") instanceof Map<?, ?> files && files.get(fileName) instanceof Map<?, ?> file) {
                    out.put("rawUrl", file.get("raw_url"));
                }
                out.put("public", body.get("public"));
            }
            default -> throw new MiniFlowException("error.param.oneOf", "operation",
                    "createIssue, comment, addLabels, close, createGist");
        }
        return NodeResult.of(out);
    }

    private Map<String, Object> call(HttpMethod method, String url, Object body, Consumer<HttpHeaders> headers) {
        HttpSupport.Response r = HttpSupport.sendJson(client, method, url, body, headers);
        Map<String, Object> json = r.json(mapper);
        if (!r.ok()) {
            Object message = json.getOrDefault("message", r.shortBody());
            throw new MiniFlowException("error.github", r.status(), message);
        }
        return json;
    }

    String gistContent(Object content, String fileName) {
        if (content == null) {
            return "";
        }
        if (content instanceof CharSequence text) {
            return text.toString();
        }
        String lower = fileName.toLowerCase();
        if (content instanceof Collection<?> rows && rows.stream().allMatch(r -> r instanceof Map<?, ?>)
                && (lower.endsWith(".md") || lower.endsWith(".csv"))) {
            if (rows.isEmpty()) {
                return "(no rows)\n";
            }
            List<String> columns = new ArrayList<>();
            rows.forEach(r -> ((Map<?, ?>) r).keySet().forEach(k -> {
                if (!columns.contains(String.valueOf(k))) {
                    columns.add(String.valueOf(k));
                }
            }));
            StringBuilder sb = new StringBuilder();
            boolean md = lower.endsWith(".md");
            if (md) {
                sb.append("| ").append(String.join(" | ", columns.stream().map(GitHubNode::mdCell).toList())).append(" |\n");
                sb.append("|").append("---|".repeat(columns.size())).append("\n");
            } else {
                sb.append(String.join(",", columns.stream().map(GitHubNode::csvCell).toList())).append("\n");
            }
            for (Object r : rows) {
                Map<?, ?> row = (Map<?, ?>) r;
                List<String> cells = columns.stream().map(col -> {
                    Object v = row.get(col);
                    String text = v == null ? "" : String.valueOf(v);
                    return md ? mdCell(text) : csvCell(text);
                }).toList();
                sb.append(md ? "| " + String.join(" | ", cells) + " |" : String.join(",", cells)).append("\n");
            }
            return sb.toString();
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        } catch (Exception e) {
            return String.valueOf(content);
        }
    }

    private static String mdCell(String s) {
        return s.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private static String csvCell(String s) {
        return s.contains(",") || s.contains("\"") || s.contains("\n")
                ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static String issue(Map<String, Object> params) {
        String n = Params.required(params, "issueNumber").trim();
        if (!n.matches("\\d+")) {
            throw new MiniFlowException("error.param.number", "issueNumber", n);
        }
        return n;
    }
}
