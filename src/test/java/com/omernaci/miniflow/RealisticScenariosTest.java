package com.omernaci.miniflow;

import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext.StepLog;
import com.omernaci.miniflow.execution.Execution;
import com.omernaci.miniflow.execution.ExecutionService;
import com.omernaci.miniflow.variable.VariableService;
import com.omernaci.miniflow.workflow.Workflow;
import com.omernaci.miniflow.workflow.WorkflowService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RealisticScenariosTest {

    static final String SHOP_DB = "jdbc:h2:mem:shop;DB_CLOSE_DELAY=-1";

    record Recorded(String method, String path, Map<String, String> headers, String body) {
    }

    record Reply(int status, String body) {
    }

    static HttpServer http;
    static FakeSmtpServer smtp;
    static final List<Recorded> requests = new CopyOnWriteArrayList<>();
    static final Map<String, Reply> replies = new ConcurrentHashMap<>();

    @Autowired CredentialService credentials;
    @Autowired VariableService variables;
    @Autowired WorkflowService store;
    @Autowired ExecutionService executions;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;

    @BeforeAll
    static void start() throws Exception {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/", ex -> {
            Map<String, String> headers = new LinkedHashMap<>();
            ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = ex.getRequestURI().getPath();
            requests.add(new Recorded(ex.getRequestMethod(), path, headers, body));
            Reply reply = replies.getOrDefault(ex.getRequestMethod() + " " + path, new Reply(200, "{}"));
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(reply.status(), bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
            ex.close();
        });
        http.start();
        smtp = new FakeSmtpServer();

        try (Connection c = DriverManager.getConnection(SHOP_DB, "sa", "");
             Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'SHOP_ORDERS'");
            rs.next();
            if (rs.getInt(1) == 0) {
                StringBuilder sql = new StringBuilder();
                for (String line : Files.readAllLines(Path.of("examples/sql/shop-schema.sql"))) {
                    if (!line.trim().startsWith("--")) {
                        sql.append(line).append('\n');
                    }
                }
                for (String statement : sql.toString().split(";")) {
                    if (!statement.isBlank()) {
                        st.execute(statement);
                    }
                }
            }
        }
    }

    @AfterAll
    static void stop() throws Exception {
        http.stop(0);
        smtp.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        requests.clear();
        replies.clear();
        smtp.mails.clear();
        upsertCredential("shop-db", "database", Map.of("jdbcUrl", SHOP_DB, "username", "sa"));
        upsertCredential("smtp", "smtp", Map.of("host", "127.0.0.1", "port", String.valueOf(smtp.port()),
                "from", "Shop <noreply@shop.test>", "starttls", "false"));
        upsertCredential("slack", "slack", Map.of("webhookUrl", base() + "/slack/ops"));
        upsertCredential("github", "github", Map.of("token", "ghp_test", "apiBase", base()));
        upsertCredential("openai", "ai", Map.of("provider", "openai", "apiKey", "sk-test", "model", "gpt-test",
                "baseUrl", base() + "/v1"));
        upsertCredential("rabbitmq", "rabbitmq", Map.of("host", "127.0.0.1", "port", "1", "username", "guest",
                "password", "guest", "virtualHost", "/", "ssl", "false"));
        store.importJson(Files.readString(Path.of("examples/realistic.json"), StandardCharsets.UTF_8), true);
    }

    static String base() {
        return "http://127.0.0.1:" + http.getAddress().getPort();
    }

    private void upsertCredential(String name, String type, Map<String, String> values) {
        boolean exists = credentials.summary(name).isPresent();
        credentials.save(exists ? name : null, name, type, values);
    }

    private Optional<String> setVariable(String name, String value) {
        Optional<String> old = variables.get(name);
        boolean inDb = variables.list().stream().anyMatch(r -> r.name().equals(name) && r.fromDb());
        variables.save(inDb ? name : null, name, value, null);
        return old;
    }

    private void restoreVariable(String name, Optional<String> old) {
        if (old.isPresent()) {
            setVariable(name, old.get());
        } else {
            variables.delete(name);
        }
    }

    private Execution run(String workflowId, Map<String, Object> data) {
        Workflow wf = store.find(workflowId).orElseThrow();
        return executions.run(wf, data, "webhook");
    }

    private static Map<String, Object> lastOutput(Execution ex) {
        return ex.steps().get(ex.steps().size() - 1).output();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(String s) throws Exception {
        return mapper.readValue(s, Map.class);
    }

    private Recorded request(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).findFirst().orElseThrow();
    }

    @Test
    void orderPipelineValidatesSavesAndIgnoresDuplicates() throws Exception {
        Execution invalid = run("order-pipeline", Map.of("orderNo", "X-1", "total", 10, "paymentStatus", "pending",
                "customer", Map.of("email", "x@example.com")));
        assertThat(lastOutput(invalid)).containsEntry("ok", false);

        Map<String, Object> order = Map.of("orderNo", "ORD-T-1", "currency", "eur", "total", 1250.0, "paymentStatus", "paid",
                "customer", Map.of("name", "Dave Tester", "email", "Dave@Example.com"),
                "items", List.of(Map.of("sku", "A", "qty", 1, "price", 1250)));
        Execution first = run("order-pipeline", order);
        assertThat(first.steps()).filteredOn(s -> s.nodeId().equals("save-order"))
                .extracting(StepLog::status).containsExactly("SUCCESS");
        assertThat(first.steps()).filteredOn(s -> s.nodeId().equals("send-to-warehouse"))
                .extracting(StepLog::status).containsExactly("ERROR");
        try (Connection c = DriverManager.getConnection(SHOP_DB, "sa", "");
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT customer_email, currency, total FROM shop_orders WHERE order_no = 'ORD-T-1'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("dave@example.com");
            assertThat(rs.getString(2)).isEqualTo("EUR");
            assertThat(rs.getDouble(3)).isEqualTo(1250.0);
        }

        Execution again = run("order-pipeline", order);
        assertThat(again.status()).isEqualTo("SUCCESS");
        assertThat(lastOutput(again)).containsEntry("status", "duplicate-ignored");
    }

    @Test
    void supportTriageUrgentRequestOpensGitHubIssuePagesSlackAndAcknowledges() throws Exception {
        replies.put("POST /v1/chat/completions", new Reply(200, "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"category\\\":\\\"billing\\\",\\\"urgency\\\":\\\"high\\\",\\\"sentiment\\\":\\\"angry\\\","
                + "\\\"summary\\\":\\\"Customer charged but no order created\\\"}\"}}]}"));
        replies.put("POST /repos/acme/shop/issues", new Reply(201,
                "{\"number\":9,\"html_url\":\"https://github.com/acme/shop/issues/9\",\"id\":1}"));
        Optional<String> oldRepo = setVariable("githubRepo", "acme/shop");
        try {
            Execution ex = run("support-triage", Map.of("name", "Bob", "email", "bob@example.com",
                    "subject", "Charged but no order", "message", "My card was charged but I see no order!"));

            assertThat(ex.status()).isEqualTo("SUCCESS");
            assertThat(lastOutput(ex)).containsEntry("category", "billing").containsEntry("urgency", "high")
                    .containsEntry("ticket", 9);

            Map<String, Object> issue = json(request("POST", "/repos/acme/shop/issues").body());
            assertThat(issue).containsEntry("title", "[billing] Charged but no order");
            assertThat(issue.get("labels")).isEqualTo(List.of("support", "billing", "urgent"));
            assertThat(String.valueOf(json(request("POST", "/slack/ops").body()).get("text")))
                    .startsWith("🔴 Urgent ticket #9 (billing, angry): Customer charged but no order created")
                    .endsWith("https://github.com/acme/shop/issues/9");

            assertThat(smtp.mails).hasSize(1);
            FakeSmtpServer.Mail mail = smtp.mails.get(0);
            assertThat(mail.to()).containsExactly("<bob@example.com>");
            assertThat(mail.data()).contains("Subject: We received your request: Charged but no order");
            assertThat(requests).noneMatch(r -> r.path().contains("spreadsheets"));
        } finally {
            restoreVariable("githubRepo", oldRepo);
        }
    }

    @Test
    void uptimeIncidentOpensOneIssuePerHourAndPagesSlack() throws Exception {
        replies.put("GET /health", new Reply(503, "{\"status\":\"maintenance\"}"));
        replies.put("POST /repos/acme/shop/issues", new Reply(201,
                "{\"number\":7,\"html_url\":\"https://github.com/acme/shop/issues/7\",\"id\":1}"));
        Optional<String> oldUrl = setVariable("healthCheckUrl", base() + "/health");
        Optional<String> oldRepo = setVariable("githubRepo", "acme/shop");
        try {
            Execution first = executions.run(store.find("uptime-incident").orElseThrow(), Map.of(), "schedule");
            assertThat(first.status()).isEqualTo("SUCCESS");
            Map<String, Object> issue = json(request("POST", "/repos/acme/shop/issues").body());
            assertThat(String.valueOf(issue.get("title"))).endsWith("/health is down (HTTP 503)");
            assertThat(issue.get("labels")).isEqualTo(List.of("incident", "monitoring"));
            assertThat(String.valueOf(json(request("POST", "/slack/ops").body()).get("text")))
                    .contains("is DOWN (HTTP 503)").contains("https://github.com/acme/shop/issues/7");

            Execution second = executions.run(store.find("uptime-incident").orElseThrow(), Map.of(), "schedule");
            assertThat(second.steps()).extracting(StepLog::nodeId).doesNotContain("open-incident");
            assertThat(requests).filteredOn(r -> r.path().equals("/repos/acme/shop/issues")).hasSize(1);
        } finally {
            restoreVariable("healthCheckUrl", oldUrl);
            restoreVariable("githubRepo", oldRepo);
        }
    }

    @Test
    void dailySalesReportEmailsExcelAndPostsSummary() throws Exception {
        Optional<String> old = setVariable("reportEmail", "management@shop.test");
        try {
            Execution ex = executions.run(store.find("daily-sales-report").orElseThrow(), Map.of(), "schedule");
            assertThat(ex.status()).isEqualTo("SUCCESS");

            assertThat(smtp.mails).hasSize(1);
            FakeSmtpServer.Mail mail = smtp.mails.get(0);
            assertThat(mail.to()).containsExactly("<management@shop.test>");
            assertThat(mail.data()).contains("Subject: Sales report: 3 orders, 1759.90 revenue")
                    .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .contains("sales-");
            assertThat(String.valueOf(json(request("POST", "/slack/ops").body()).get("text")))
                    .startsWith("📈 Yesterday: 3 orders · revenue 1759.90");
        } finally {
            restoreVariable("reportEmail", old);
        }
    }

    @Test
    void exportWorkerSendsCustomerTheirOrdersAsExcel() {
        Execution ex = executions.run(store.find("export-worker").orElseThrow(),
                Map.of("body", Map.of("email", "alice@example.com", "requestedAt", "2026-11-04T10:00:00")), "queue");
        assertThat(ex.status()).isEqualTo("SUCCESS");
        assertThat(smtp.mails).hasSize(1);
        FakeSmtpServer.Mail mail = smtp.mails.get(0);
        assertThat(mail.to()).containsExactly("<alice@example.com>");
        assertThat(mail.data()).contains("(2 orders)").contains("my-orders-");
    }

    @Test
    void pullRequestWebhookCommentsOnLinkedIssueAndNotifiesSlack() throws Exception {
        replies.put("POST /repos/acme/shop/issues/142/comments", new Reply(201,
                "{\"id\":5,\"html_url\":\"https://github.com/acme/shop/issues/142#c5\"}"));
        store.setActive("pr-issue-sync", true);
        try {
            mvc.perform(post("/webhook/github-pr").contentType(MediaType.APPLICATION_JSON)
                            .header("X-GitHub-Event", "pull_request")
                            .content("{\"action\":\"closed\",\"repository\":{\"full_name\":\"acme/shop\"},"
                                    + "\"pull_request\":{\"number\":77,\"title\":\"Add PayPal checkout\","
                                    + "\"body\":\"Implements the new payment flow.\\n\\nFixes #142\","
                                    + "\"html_url\":\"https://github.com/acme/shop/pull/77\",\"merged\":true,"
                                    + "\"user\":{\"login\":\"omer\"}}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.issue").value("142"));
            assertThat(json(request("POST", "/repos/acme/shop/issues/142/comments").body()).get("body"))
                    .isEqualTo("Pull request #77 merged by @omer: Add PayPal checkout\nhttps://github.com/acme/shop/pull/77");
            assertThat(String.valueOf(json(request("POST", "/slack/ops").body()).get("text")))
                    .isEqualTo("🔀 PR #77 merged by omer: <https://github.com/acme/shop/pull/77|Add PayPal checkout> · linked issue #142");

            mvc.perform(post("/webhook/github-pr").contentType(MediaType.APPLICATION_JSON)
                            .header("X-GitHub-Event", "pull_request")
                            .content("{\"action\":\"opened\",\"repository\":{\"full_name\":\"acme/shop\"},"
                                    + "\"pull_request\":{\"number\":78,\"title\":\"Fix typo\",\"user\":{\"login\":\"x\"}}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ignored").value(true));
        } finally {
            store.setActive("pr-issue-sync", false);
        }
    }
}
