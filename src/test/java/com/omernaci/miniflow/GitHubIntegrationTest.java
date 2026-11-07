package com.omernaci.miniflow;

import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.engine.WorkflowEngine;
import com.omernaci.miniflow.execution.Execution;
import com.omernaci.miniflow.execution.ExecutionService;
import com.omernaci.miniflow.workflow.Workflow;
import com.omernaci.miniflow.workflow.Workflow.Connection;
import com.omernaci.miniflow.workflow.Workflow.NodeDef;
import com.omernaci.miniflow.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GitHubIntegrationTest {

    @Autowired
    WorkflowService store;

    @Autowired
    ExecutionService executions;

    @Autowired
    WorkflowEngine engine;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    MockMvc mvc;

    @Test
    void dedupeRemembersKeysAcrossRunsButNotInManualTests() {
        Workflow wf = new Workflow("dedupe-test", "dedupe", List.of(
                new NodeDef("s", "trigger", Map.of()),
                new NodeDef("d", "dedupe", Map.of("key", "{{ input.id }}")),
                new NodeDef("yeni", "set", Map.of("keepInput", false, "values", Map.of("r", "new"))),
                new NodeDef("eski", "set", Map.of("keepInput", false, "values", Map.of("r", "seen")))
        ), List.of(new Connection("s", "d", null),
                new Connection("d", "yeni", "new"), new Connection("d", "eski", "seen")));

        assertThat(result(executions.run(wf, Map.of("id", "1"), "schedule"))).isEqualTo("new");
        assertThat(result(executions.run(wf, Map.of("id", "1"), "schedule"))).isEqualTo("seen");
        assertThat(result(executions.run(wf, Map.of("id", "2"), "schedule"))).isEqualTo("new");

        assertThat(result(executions.run(wf, Map.of("id", "3"), "manual"))).isEqualTo("new");
        assertThat(result(executions.run(wf, Map.of("id", "3"), "manual"))).isEqualTo("new");
        assertThat(result(executions.run(wf, Map.of("id", "3"), "schedule"))).isEqualTo("new");
        assertThat(result(executions.run(wf, Map.of("id", "3"), "manual"))).isEqualTo("seen");
    }

    private static Object result(Execution ex) {
        assertThat(ex.status()).isEqualTo("SUCCESS");
        return ex.steps().get(ex.steps().size() - 1).output().get("r");
    }

    @Test
    void githubWebhookRoutesByEventHeader() throws Exception {
        store.importJson(Files.readString(Path.of("examples/github.json"), StandardCharsets.UTF_8), true);

        mvc.perform(post("/webhook/github").contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "ping")
                        .content("{\"zen\":\"Keep it simple.\",\"hook_id\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message", startsWith("pong")))
                .andExpect(jsonPath("$.data.hookId").value(123));

        mvc.perform(post("/webhook/github").contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "workflow_run")
                        .content("{\"action\":\"completed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ignored").value("'workflow_run' event not handled"));
    }

    @Test
    void webhookSignatureIsVerifiedWhenSecretIsConfigured() throws Exception {
        store.save(new Workflow("imzali", "İmzalı", true, List.of(
                new NodeDef("s", "trigger", Map.of("path", "imzali", "signatureSecretVar", "testSecret")),
                new NodeDef("r", "set", Map.of("keepInput", false, "values", Map.of("ok", true)))
        ), List.of(new Connection("s", "r", null))));

        String body = "{\"hello\":\"world\"}";
        mvc.perform(post("/webhook/imzali").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign("s3cr3t", body)).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        mvc.perform(post("/webhook/imzali").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign("yanlis", body)).content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/webhook/imzali").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        store.delete("imzali");
    }

    @Test
    void webhookExposesHeadersAndAcceptsForms() throws Exception {
        store.save(new Workflow("basliklar", "Başlıklar", true, List.of(
                new NodeDef("s", "trigger", Map.of("path", "basliklar")),
                new NodeDef("r", "set", Map.of("keepInput", false, "values", Map.of(
                        "event", "{{ input._headers['x-github-event'] }}",
                        "name", "{{ input.name }}",
                        "auth", "{{ input._headers['authorization'] == null }}")))
        ), List.of(new Connection("s", "r", null))));

        mvc.perform(post("/webhook/basliklar")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("X-GitHub-Event", "push")
                        .header("Authorization", "Bearer gizli")
                        .param("name", "Ömer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event").value("push"))
                .andExpect(jsonPath("$.data.name").value("Ömer"))
                .andExpect(jsonPath("$.data.auth").value(true));
        store.delete("basliklar");
    }

    private static String sign(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, Object> runNode(String flowId, String nodeId, Map<String, Object> input) throws Exception {
        List<Workflow> flows = mapper.readValue(
                Files.readString(Path.of("examples/github.json"), StandardCharsets.UTF_8),
                new TypeReference<>() { });
        NodeDef node = flows.stream().filter(f -> f.id().equals(flowId)).findFirst().orElseThrow()
                .node(nodeId).orElseThrow();
        Workflow mini = new Workflow("expr-" + flowId, "x", List.of(new NodeDef("s", "trigger", Map.of()), node),
                List.of(new Connection("s", nodeId, null)));
        ExecutionContext ctx = engine.run(mini, input);
        assertThat(ctx.steps().get(1).error()).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) ctx.nodeOutputs().get(nodeId);
        return out;
    }

    @Test
    void activityMessagesForDifferentEventTypes() throws Exception {
        Map<String, Object> push = new LinkedHashMap<>(Map.of("type", "PushEvent", "actor", "omernaci",
                "repo", "omernaci/medium-samples", "ref", "refs/heads/main", "head", "abc123"));
        assertThat(runNode("github-activity", "message", push).get("text")).isEqualTo(
                "📦 omernaci pushed to omernaci/medium-samples@main: "
                        + "https://github.com/omernaci/medium-samples/commit/abc123");

        Map<String, Object> create = Map.of("type", "CreateEvent", "actor", "omernaci",
                "repo", "omernaci/new-project", "refType", "repository");
        assertThat(runNode("github-activity", "message", create).get("text"))
                .isEqualTo("🆕 omernaci created repository in omernaci/new-project");

        Map<String, Object> issue = Map.of("type", "IssuesEvent", "actor", "omernaci",
                "repo", "omernaci/medium-samples", "action", "opened");
        assertThat(runNode("github-activity", "message", issue).get("text"))
                .isEqualTo("🔔 omernaci · omernaci/medium-samples: IssuesEvent (opened)");
    }

    @Test
    void activityExtractsFieldsFromEventsApiResponse() throws Exception {
        Map<String, Object> apiResponse = Map.of("ok", true, "body", List.of(Map.of(
                "id", "12345", "type", "PushEvent", "created_at", "2026-11-04T19:00:00Z",
                "actor", Map.of("login", "omernaci"),
                "repo", Map.of("name", "omernaci/medium-samples"),
                "payload", Map.of("ref", "refs/heads/main", "head", "abc123"))));
        Map<String, Object> out = runNode("github-activity", "latest-event", apiResponse);
        assertThat(out).containsEntry("id", "12345").containsEntry("repo", "omernaci/medium-samples")
                .containsEntry("head", "abc123").containsEntry("action", null);
    }

    @Test
    void weeklySummaryUsesSelectionAndProjection() throws Exception {
        Map<String, Object> apiResponse = Map.of("body", List.of(
                Map.of("name", "medium-samples", "stargazers_count", 3, "fork", false),
                Map.of("name", "spring-petclinic", "stargazers_count", 0, "fork", true)));
        assertThat(runNode("github-weekly-summary", "summary", apiResponse).get("text")).isEqualTo(
                "📊 omernaci weekly summary: 1 own repos, 1 forks\n"
                        + "Recently updated: medium-samples ★3, spring-petclinic ★0");
    }

    @Test
    void authorizationHeaderIsOmittedWithoutToken() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) mapper.readValue(
                Files.readString(Path.of("examples/workflows/github-activity.json"), StandardCharsets.UTF_8), Workflow.class)
                .node("events").orElseThrow().params().get("headers");
        Workflow mini = new Workflow("hdr", "x", List.of(
                new NodeDef("s", "trigger", Map.of()),
                new NodeDef("h", "set", Map.of("keepInput", false, "values", headers))
        ), List.of(new Connection("s", "h", null)));
        Map<?, ?> out = (Map<?, ?>) engine.run(mini, Map.of()).nodeOutputs().get("h");
        assertThat(out.get("Authorization")).isNull();
        assertThat(out.get("User-Agent")).isEqualTo("MiniFlow");
    }

    @Test
    void devJournalCollectsCommitsAndPullRequests() throws Exception {
        List<Workflow> flows = mapper.readValue(
                Files.readString(Path.of("examples/github.json"), StandardCharsets.UTF_8), new TypeReference<>() { });
        NodeDef collect = flows.stream().filter(f -> f.id().equals("dev-journal")).findFirst().orElseThrow()
                .node("collect").orElseThrow();
        Map<String, Object> commits = Map.of("body", Map.of("total_count", 2, "items", List.of(
                Map.of("repository", Map.of("full_name", "omernaci/miniflow"),
                        "commit", Map.of("message", "Add Slack block builder\n\nLonger description")),
                Map.of("repository", Map.of("full_name", "omernaci/miniflow"),
                        "commit", Map.of("message", "Fix pagination")))));
        Map<String, Object> prs = Map.of("body", Map.of("total_count", 1, "items", List.of(
                Map.of("repository_url", "https://api.github.com/repos/omernaci/miniflow",
                        "title", "Dashboard line chart", "state", "open"))));
        Workflow mini = new Workflow("journal", "x", List.of(
                new NodeDef("s", "trigger", Map.of()),
                new NodeDef("commits", "set", Map.of("keepInput", false, "values", commits)),
                new NodeDef("prs", "set", Map.of("keepInput", false, "values", prs)),
                collect
        ), List.of(new Connection("s", "commits", null), new Connection("commits", "prs", null),
                new Connection("prs", "collect", null)));
        ExecutionContext ctx = engine.run(mini, Map.of());
        assertThat(ctx.steps()).allSatisfy(step -> assertThat(step.error()).isNull());
        Map<?, ?> out = (Map<?, ?>) ctx.nodeOutputs().get("collect");
        assertThat(((Number) out.get("total")).intValue()).isEqualTo(3);
        assertThat(out.get("commitLines")).isEqualTo(
                "- omernaci/miniflow: Add Slack block builder\n- omernaci/miniflow: Fix pagination");
        assertThat(out.get("prLines")).isEqualTo("- omernaci/miniflow: Dashboard line chart (open)");
    }
}
