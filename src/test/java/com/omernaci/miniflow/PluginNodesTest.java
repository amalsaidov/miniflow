package com.omernaci.miniflow;

import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.engine.ExecutionContext.StepLog;
import com.omernaci.miniflow.engine.WorkflowEngine;
import com.omernaci.miniflow.workflow.Workflow;
import com.omernaci.miniflow.workflow.Workflow.Connection;
import com.omernaci.miniflow.workflow.Workflow.NodeDef;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PluginNodesTest {

    record Recorded(String method, String path, Map<String, String> headers, String body) {
    }

    record Reply(int status, String contentType, String body) {
    }

    static HttpServer server;
    static final List<Recorded> requests = new CopyOnWriteArrayList<>();
    static final Map<String, Reply> replies = new ConcurrentHashMap<>();

    @Autowired
    CredentialService credentials;

    @Autowired
    WorkflowEngine engine;

    @Autowired
    ObjectMapper mapper;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            requests.add(new Recorded(exchange.getRequestMethod(), path, headers, body));

            Reply reply = replies.getOrDefault(path, new Reply(200, "application/json", "{}"));
            exchange.getResponseHeaders().set("Content-Type", reply.contentType());
            if (reply.status() == 204) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void reset() {
        requests.clear();
        replies.clear();
    }

    static String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String credential(String type, Map<String, String> values) {
        String name = type + "-" + UUID.randomUUID().toString().substring(0, 8);
        credentials.save(null, name, type, values);
        return name;
    }

    private StepLog run(String type, Map<String, Object> params, Map<String, Object> input) {
        Workflow wf = new Workflow("plugin-" + type, "x", List.of(
                new NodeDef("s", "trigger", Map.of()),
                new NodeDef("n", type, params)
        ), List.of(new Connection("s", "n", null)));
        ExecutionContext ctx = engine.run(wf, input);
        return ctx.steps().get(1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(String body) throws Exception {
        return mapper.readValue(body, Map.class);
    }

    @Test
    void slackPostsToWebhookAndHidesUrlOnError() throws Exception {
        String slack = credential("slack", Map.of("webhookUrl", base() + "/slack/T1"));
        replies.put("/slack/T1", new Reply(200, "text/plain", "ok"));
        StepLog s = run("slack", Map.of("credential", slack, "text", "Deploy bitti: {{ input.surum }}"),
                Map.of("surum", "1.2.0"));
        assertThat(s.status()).isEqualTo("SUCCESS");
        assertThat(json(requests.get(0).body())).containsEntry("text", "Deploy bitti: 1.2.0");

        StepLog blocks = run("slack", Map.of("credential", slack, "text", "Report",
                "blocks", List.of(
                        Map.of("type", "header", "text", Map.of("type", "plain_text", "text", "Report {{ input.day }}")),
                        Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", "{{ input.n }} runs")))),
                Map.of("day", "Mon", "n", 5));
        assertThat(blocks.status()).isEqualTo("SUCCESS");
        Map<String, Object> sent = json(requests.get(1).body());
        assertThat(sent).containsEntry("text", "Report");
        assertThat(sent.get("blocks").toString()).contains("Report Mon").contains("5 runs");

        replies.put("/slack/T1", new Reply(404, "text/plain", "no_service"));
        StepLog fail = run("slack", Map.of("credential", slack, "text", "x"), Map.of());
        assertThat(fail.status()).isEqualTo("ERROR");
        assertThat(fail.error()).contains("no_service").doesNotContain("/slack/T1");
    }

    @Test
    void aiOpenAiCompatibleWithJsonOutput() throws Exception {
        String cred = credential("ai", Map.of("provider", "openai", "apiKey", "sk-test",
                "model", "test-model", "baseUrl", base() + "/v1"));
        replies.put("/v1/chat/completions", new Reply(200, "application/json",
                "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"kategori\\\":\\\"fatura\\\"}\\n```\"}}],"
                        + "\"usage\":{\"total_tokens\":12}}"));

        StepLog step = run("ai", Map.of("credential", cred, "system", "Sınıflandır",
                "prompt", "Talep: {{ input.konu }}", "json", true, "maxTokens", 50), Map.of("konu", "Faturam yanlış"));
        assertThat(step.status()).isEqualTo("SUCCESS");
        assertThat(step.output().get("data")).isEqualTo(Map.of("kategori", "fatura"));

        Recorded r = requests.get(0);
        assertThat(r.headers()).containsEntry("authorization", "Bearer sk-test");
        Map<String, Object> body = json(r.body());
        assertThat(body).containsEntry("model", "test-model").containsEntry("max_tokens", 50);
        assertThat(body.get("messages").toString()).contains("Sınıflandır").contains("Talep: Faturam yanlış");
    }

    @Test
    void aiGeminiInteractionsApi() throws Exception {
        String cred = credential("ai", Map.of("provider", "gemini", "apiKey", "AIza-test",
                "model", "models/gemini-test", "baseUrl", base()));
        replies.put("/v1beta/interactions", new Reply(200, "application/json",
                "{\"id\":\"int_1\",\"status\":\"completed\",\"steps\":["
                        + "{\"type\":\"thought\",\"content\":[{\"type\":\"text\",\"text\":\"thinking\"}]},"
                        + "{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"{\\\"category\\\":\\\"billing\\\"}\"}]}],"
                        + "\"usage\":{\"total_tokens\":21}}"));

        StepLog step = run("ai", Map.of("credential", cred, "system", "Classify", "prompt", "Ticket: {{ input.subject }}",
                "json", true, "maxTokens", 200, "thinking", "low"), Map.of("subject", "Invoice wrong"));
        assertThat(step.status()).isEqualTo("SUCCESS");
        assertThat(step.output()).containsEntry("text", "{\"category\":\"billing\"}").containsEntry("provider", "gemini");
        assertThat(step.output().get("data")).isEqualTo(Map.of("category", "billing"));

        Recorded r = requests.get(0);
        assertThat(r.headers()).containsEntry("x-goog-api-key", "AIza-test").doesNotContainKey("authorization");
        Map<String, Object> body = json(r.body());
        assertThat(body).containsEntry("model", "gemini-test").containsEntry("system_instruction", "Classify")
                .containsEntry("input", "Ticket: Invoice wrong");
        assertThat(body.get("generation_config")).isEqualTo(Map.of("max_output_tokens", 200, "thinking_level", "low"));

        replies.put("/v1beta/interactions", new Reply(200, "application/json",
                "{\"id\":\"int_2\",\"status\":\"incomplete\",\"steps\":[]}"));
        StepLog empty = run("ai", Map.of("credential", cred, "prompt", "x"), Map.of());
        assertThat(empty.status()).isEqualTo("ERROR");
        assertThat(empty.error()).contains("incomplete").doesNotContain("AIza-test");

        replies.put("/v1beta/interactions", new Reply(400, "application/json",
                "{\"error\":{\"code\":400,\"message\":\"API key not valid.\"}}"));
        assertThat(run("ai", Map.of("credential", cred, "prompt", "x"), Map.of()).error()).contains("API key not valid");
    }

    @Test
    void aiAnthropic() throws Exception {
        String cred = credential("ai", Map.of("provider", "anthropic", "apiKey", "ak-test",
                "model", "claude-test", "baseUrl", base()));
        replies.put("/v1/messages", new Reply(200, "application/json",
                "{\"content\":[{\"type\":\"text\",\"text\":\"Merhaba!\"}],\"usage\":{\"output_tokens\":3}}"));

        StepLog step = run("ai", Map.of("credential", cred, "prompt", "Selam ver"), Map.of());
        assertThat(step.status()).isEqualTo("SUCCESS");
        assertThat(step.output()).containsEntry("text", "Merhaba!").containsEntry("provider", "anthropic");
        Recorded r = requests.get(0);
        assertThat(r.headers()).containsEntry("x-api-key", "ak-test").containsEntry("anthropic-version", "2023-06-01");
        assertThat(json(r.body())).containsEntry("max_tokens", 1024);

        replies.put("/v1/messages", new Reply(401, "application/json",
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"));
        StepLog err = run("ai", Map.of("credential", cred, "prompt", "x"), Map.of());
        assertThat(err.status()).isEqualTo("ERROR");
        assertThat(err.error()).contains("401").contains("invalid x-api-key");
    }

    @Test
    void rssAndAtomFeeds() {
        replies.put("/feed.xml", new Reply(200, "application/rss+xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>Blog</title>
                  <item><title>İkinci yazı</title><link>https://ornek.com/2</link>
                        <pubDate>Wed, 04 Nov 2026 10:00:00 GMT</pubDate>
                        <description><![CDATA[<p>Merhaba <b>dünya</b></p>]]></description></item>
                  <item><title>İlk yazı</title><link>https://ornek.com/1</link></item>
                </channel></rss>"""));
        StepLog rss = run("rss", Map.of("url", base() + "/feed.xml"), Map.of());
        assertThat(rss.status()).isEqualTo("SUCCESS");
        assertThat(rss.output()).containsEntry("title", "Blog").containsEntry("count", 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> latest = (Map<String, Object>) rss.output().get("latest");
        assertThat(latest).containsEntry("title", "İkinci yazı").containsEntry("link", "https://ornek.com/2")
                .containsEntry("summary", "Merhaba dünya").containsEntry("id", "https://ornek.com/2");

        replies.put("/atom.xml", new Reply(200, "application/atom+xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom"><title>Sürümler</title>
                  <entry><id>tag:v2</id><title>v2.0.0</title><updated>2026-11-01T00:00:00Z</updated>
                    <link rel="alternate" href="https://ornek.com/releases/v2"/></entry>
                </feed>"""));
        StepLog atom = run("rss", Map.of("url", base() + "/atom.xml", "limit", 1), Map.of());
        assertThat(atom.status()).isEqualTo("SUCCESS");
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) atom.output().get("latest");
        assertThat(entry).containsEntry("title", "v2.0.0").containsEntry("link", "https://ornek.com/releases/v2")
                .containsEntry("published", "2026-11-01T00:00:00Z");

        replies.put("/xxe.xml", new Reply(200, "application/xml",
                "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><rss><channel><item><title>&x;</title></item></channel></rss>"));
        assertThat(run("rss", Map.of("url", base() + "/xxe.xml"), Map.of()).status()).isEqualTo("ERROR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void databaseQueriesWithNamedParameters() {
        String cred = credential("database", Map.of("jdbcUrl", "jdbc:h2:mem:plugin-db;DB_CLOSE_DELAY=-1", "username", "sa"));

        assertThat(run("database", Map.of("credential", cred,
                "query", "CREATE TABLE IF NOT EXISTS siparis (id INT PRIMARY KEY, musteri VARCHAR(50), durum VARCHAR(20))"),
                Map.of()).status()).isEqualTo("SUCCESS");

        StepLog insert = run("database", Map.of("credential", cred,
                "query", "MERGE INTO siparis KEY(id) VALUES (:id, :musteri, :durum)",
                "params", Map.of("id", "{{ input.id }}", "musteri", "{{ input.musteri }}", "durum", "yeni")),
                Map.of("id", 1, "musteri", "Ayşe'; DROP TABLE siparis; --"));
        assertThat(insert.status()).isEqualTo("SUCCESS");
        assertThat(insert.output()).containsEntry("updated", 1);

        StepLog select = run("database", Map.of("credential", cred,
                "query", "SELECT id, musteri FROM siparis WHERE durum = :durum",
                "params", Map.of("durum", "yeni")), Map.of());
        assertThat(select.status()).isEqualTo("SUCCESS");
        assertThat(select.output()).containsEntry("count", 1);
        Map<String, Object> first = (Map<String, Object>) select.output().get("first");
        assertThat(first).containsEntry("musteri", "Ayşe'; DROP TABLE siparis; --");

        StepLog bad = run("database", Map.of("credential", cred, "query", "SELECT * FROM yok_boyle_tablo"), Map.of());
        assertThat(bad.status()).isEqualTo("ERROR");
    }

    @Test
    void httpNodeAddsCredentialHeader() {
        String cred = credential("http-header", Map.of("headerName", "Authorization", "headerValue", "Bearer gizli"));
        StepLog step = run("http", Map.of("url", base() + "/secure", "credential", cred), Map.of());
        assertThat(step.status()).isEqualTo("SUCCESS");
        assertThat(requests.get(0).headers()).containsEntry("authorization", "Bearer gizli");
    }

    @Test
    void waitNodeValidatesRange() {
        assertThat(run("wait", Map.of("seconds", 0), Map.of("a", 1)).output()).containsEntry("a", 1);
        assertThat(run("wait", Map.of("seconds", 999), Map.of()).status()).isEqualTo("ERROR");
    }

    @Test
    void credentialErrorsAreClear() {
        StepLog missing = run("email", Map.of("to", "a@b.com"), Map.of());
        assertThat(missing.status()).isEqualTo("ERROR");
        assertThat(missing.error()).contains("smtp");

        String sl = credential("slack", Map.of("webhookUrl", "http://x"));
        StepLog wrongType = run("email", Map.of("credential", sl, "to", "a@b.com"), Map.of());
        assertThat(wrongType.status()).isEqualTo("ERROR");
        assertThat(wrongType.error()).contains(sl).contains("slack");

        String smtp = credential("smtp", Map.of("host", "127.0.0.1", "port", "1", "from", "a@b.com", "starttls", "false"));
        StepLog smtpErr = run("email", Map.of("credential", smtp, "to", "x@y.com", "subject", "s", "text", "t"), Map.of());
        assertThat(smtpErr.status()).isEqualTo("ERROR");
    }
}
