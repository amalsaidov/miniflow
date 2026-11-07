package com.omernaci.miniflow;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.engine.ExecutionContext.StepLog;
import com.omernaci.miniflow.engine.WorkflowEngine;
import com.omernaci.miniflow.execution.Execution;
import com.omernaci.miniflow.execution.ExecutionService;
import com.omernaci.miniflow.execution.ExecutionStore;
import com.omernaci.miniflow.queue.QueueListenerManager;
import com.omernaci.miniflow.queue.QueueSupport;
import com.omernaci.miniflow.variable.VariableService;
import com.omernaci.miniflow.workflow.Workflow;
import com.omernaci.miniflow.workflow.Workflow.Connection;
import com.omernaci.miniflow.workflow.Workflow.NodeDef;
import com.omernaci.miniflow.workflow.WorkflowService;
import com.sun.net.httpserver.HttpServer;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IntegrationNodesTest {

    record Recorded(String method, String path, String query, Map<String, String> headers, String body) {
    }

    record Reply(int status, String body) {
    }

    static HttpServer server;
    static final List<Recorded> requests = new CopyOnWriteArrayList<>();
    static final Map<String, Reply> replies = new ConcurrentHashMap<>();
    static KeyPair googleKeys;

    @Autowired CredentialService credentials;
    @Autowired WorkflowEngine engine;
    @Autowired ObjectMapper mapper;
    @Autowired WorkflowService store;
    @Autowired ExecutionService executions;
    @Autowired ExecutionStore executionStore;
    @Autowired QueueListenerManager listeners;
    @Autowired QueueSupport queues;
    @Autowired MockMvc mvc;

    @BeforeAll
    static void start() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        googleKeys = gen.generateKeyPair();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            Map<String, String> headers = new LinkedHashMap<>();
            ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = ex.getRequestURI().getPath();
            requests.add(new Recorded(ex.getRequestMethod(), path, ex.getRequestURI().getQuery(), headers, body));
            Reply reply = replies.getOrDefault(ex.getRequestMethod() + " " + path, new Reply(200, "{}"));
            ex.getResponseHeaders().set("Content-Type", "application/json");
            if (reply.status() == 204) {
                ex.sendResponseHeaders(204, -1);
            } else {
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(reply.status(), bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            }
            ex.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
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
        Workflow wf = new Workflow("int-" + type, "x", List.of(
                new NodeDef("s", "trigger", Map.of()), new NodeDef("n", type, params)
        ), List.of(new Connection("s", "n", null)));
        ExecutionContext ctx = engine.run(wf, input);
        return ctx.steps().get(1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(String s) throws Exception {
        return mapper.readValue(s, Map.class);
    }

    @Test
    void githubCreateIssueCloseAndErrors() throws Exception {
        String cred = credential("github", Map.of("token", "ghp_test", "apiBase", base()));
        replies.put("POST /repos/omer/demo/issues", new Reply(201,
                "{\"number\":12,\"html_url\":\"https://github.com/omer/demo/issues/12\",\"id\":99}"));

        StepLog created = run("github", Map.of("credential", cred, "operation", "createIssue", "repo", "omer/demo",
                        "title", "Site çöktü: {{ input.url }}", "body", "HTTP {{ input.status }}", "labels", "bug, acil"),
                Map.of("url", "https://ornek.com", "status", 503));
        assertThat(created.status()).isEqualTo("SUCCESS");
        assertThat(created.output()).containsEntry("number", 12).containsEntry("url", "https://github.com/omer/demo/issues/12");
        Recorded r = requests.get(0);
        assertThat(r.headers()).containsEntry("authorization", "Bearer ghp_test");
        Map<String, Object> body = json(r.body());
        assertThat(body).containsEntry("title", "Site çöktü: https://ornek.com").containsEntry("body", "HTTP 503");
        assertThat(body.get("labels")).isEqualTo(List.of("bug", "acil"));

        replies.put("PATCH /repos/omer/demo/issues/12", new Reply(200, "{\"number\":12,\"state\":\"closed\"}"));
        StepLog closed = run("github", Map.of("credential", cred, "operation", "close", "repo", "omer/demo",
                "issueNumber", 12, "body", "Düzeldi"), Map.of());
        assertThat(closed.status()).isEqualTo("SUCCESS");
        assertThat(closed.output()).containsEntry("state", "closed");
        assertThat(requests).extracting(Recorded::method).contains("PATCH");

        replies.put("POST /repos/omer/demo/issues/5/comments", new Reply(404, "{\"message\":\"Not Found\"}"));
        StepLog err = run("github", Map.of("credential", cred, "operation", "comment", "repo", "omer/demo",
                "issueNumber", "5", "body", "x"), Map.of());
        assertThat(err.status()).isEqualTo("ERROR");
        assertThat(err.error()).contains("404").contains("Not Found").doesNotContain("ghp_test");

        assertThat(run("github", Map.of("credential", cred, "repo", "gecersiz depo", "title", "x"), Map.of()).status())
                .isEqualTo("ERROR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void githubCreateGistRendersRowsAsMarkdownTable() throws Exception {
        String cred = credential("github", Map.of("token", "ghp_test", "apiBase", base()));
        replies.put("POST /gists", new Reply(201, "{\"id\":\"abc123\",\"html_url\":\"https://gist.github.com/omer/abc123\","
                + "\"files\":{\"report.md\":{\"raw_url\":\"https://gist.githubusercontent.com/raw/report.md\"}}}"));
        List<Map<String, Object>> rows = List.of(row("Order | flow", 3), row("Uptime", 1));

        StepLog gist = run("github", Map.of("credential", cred, "operation", "createGist", "fileName", "report.md",
                "description", "Report", "content", "{{ input.rows }}"), Map.of("rows", rows));
        assertThat(gist.status()).isEqualTo("SUCCESS");
        assertThat(gist.output()).containsEntry("id", "abc123").containsEntry("url", "https://gist.github.com/omer/abc123")
                .containsEntry("rawUrl", "https://gist.githubusercontent.com/raw/report.md").containsEntry("public", false);

        Map<String, Object> body = json(requests.get(0).body());
        assertThat(body).containsEntry("description", "Report").containsEntry("public", false);
        Map<String, Object> file = (Map<String, Object>) ((Map<String, Object>) body.get("files")).get("report.md");
        assertThat(file.get("content")).isEqualTo(
                "| workflow | runs |\n|---|---|\n| Order \\| flow | 3 |\n| Uptime | 1 |\n");

        run("github", Map.of("credential", cred, "operation", "createGist", "fileName", "r.csv", "content", "{{ input.rows }}"),
                Map.of("rows", rows));
        Map<String, Object> csv = (Map<String, Object>) ((Map<String, Object>) json(requests.get(1).body()).get("files")).get("r.csv");
        assertThat(csv.get("content")).isEqualTo("workflow,runs\nOrder | flow,3\nUptime,1\n");
        run("github", Map.of("credential", cred, "operation", "createGist", "fileName", "notes.txt", "content", "Hello"), Map.of());
        Map<String, Object> txt = (Map<String, Object>) ((Map<String, Object>) json(requests.get(2).body()).get("files")).get("notes.txt");
        assertThat(txt.get("content")).isEqualTo("Hello");

        assertThat(run("github", Map.of("credential", cred, "operation", "createGist", "fileName", "../x.md",
                "content", "x"), Map.of()).status()).isEqualTo("ERROR");
        assertThat(run("github", Map.of("credential", cred, "operation", "createGist", "fileName", "x.md",
                "content", ""), Map.of()).status()).isEqualTo("ERROR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sqlReportToGistExampleRunsEndToEnd() throws Exception {
        upsert("github", "github", Map.of("token", "ghp_test", "apiBase", base()));
        upsert("miniflow-db", "database", Map.of("jdbcUrl", "jdbc:h2:mem:miniflow-test", "username", "sa"));
        replies.put("POST /gists", new Reply(201, "{\"id\":\"g1\",\"html_url\":\"https://gist.github.com/g1\"}"));

        executions.run(new Workflow("gist-sample", "Gist sample", List.of(new NodeDef("s", "trigger", Map.of())),
                List.of()), Map.of(), "manual");

        store.importJson(Files.readString(Path.of("examples/github.json")), true);
        Execution ex = executions.run(store.find("sql-report-to-gist").orElseThrow(), Map.of(), "schedule");
        assertThat(ex.status()).isEqualTo("SUCCESS");
        assertThat(ex.steps().get(ex.steps().size() - 1).output()).containsEntry("gist", "https://gist.github.com/g1");

        Map<String, Object> body = json(requests.stream().filter(r -> r.path().equals("/gists")).findFirst().orElseThrow().body());
        Map<String, Object> files = (Map<String, Object>) body.get("files");
        assertThat(files).hasSize(1);
        String name = files.keySet().iterator().next();
        assertThat(name).startsWith("miniflow-weekly-").endsWith(".md");
        String content = String.valueOf(((Map<String, Object>) files.get(name)).get("content"));
        assertThat(content).startsWith("| workflow | runs | succeeded | failed | avg_ms |").contains("| Gist sample |");
    }

    private void upsert(String name, String type, Map<String, String> values) {
        credentials.save(credentials.summary(name).isPresent() ? name : null, name, type, values);
    }

    private String serviceAccountJson() throws Exception {
        return serviceAccountJson("miniflow@proje.iam.gserviceaccount.com");
    }

    private String serviceAccountJson(String clientEmail) throws Exception {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(googleKeys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Map<String, Object> sa = new LinkedHashMap<>();
        sa.put("type", "service_account");
        sa.put("client_email", clientEmail);
        sa.put("private_key", pem);
        sa.put("token_uri", base() + "/token");
        return mapper.writeValueAsString(sa);
    }

    @Test
    void googleSheetsAppendAndReadWithSignedJwt() throws Exception {
        String clientEmail = "jwt-" + UUID.randomUUID() + "@proje.iam.gserviceaccount.com";
        String cred = credential("google", Map.of("serviceAccountJson", serviceAccountJson(clientEmail), "apiBase", base()));
        replies.put("POST /token", new Reply(200, "{\"access_token\":\"ya29.test\",\"expires_in\":3600}"));
        replies.put("POST /v4/spreadsheets/SHEET1/values/Sayfa1:append",
                new Reply(200, "{\"updates\":{\"updatedRange\":\"Sayfa1!A2:C2\"}}"));

        StepLog appended = run("googleSheets", Map.of("credential", cred, "spreadsheetId", "SHEET1", "range", "Sayfa1",
                        "values", "{{ input }}", "columns", List.of("tarih", "ad", "puan")),
                Map.of("ad", "Ali", "puan", 5, "tarih", "2026-11-04", "fazla", "x"));
        assertThat(appended.status()).isEqualTo("SUCCESS");
        assertThat(appended.output()).containsEntry("updatedRange", "Sayfa1!A2:C2");

        Recorded tokenReq = requests.get(0);
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : tokenReq.body().split("&")) {
            String[] kv = pair.split("=", 2);
            form.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        assertThat(form.get("grant_type")).isEqualTo("urn:ietf:params:oauth:grant-type:jwt-bearer");
        String[] jwt = form.get("assertion").split("\\.");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(googleKeys.getPublic());
        verifier.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(jwt[2]))).isTrue();
        Map<String, Object> claims = json(new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8));
        assertThat(claims).containsEntry("iss", clientEmail)
                .containsEntry("scope", "https://www.googleapis.com/auth/spreadsheets")
                .containsEntry("aud", base() + "/token");

        Recorded appendReq = requests.get(1);
        assertThat(appendReq.headers()).containsEntry("authorization", "Bearer ya29.test");
        assertThat(appendReq.query()).contains("valueInputOption=USER_ENTERED");
        assertThat(json(appendReq.body()).get("values")).isEqualTo(List.of(List.of("2026-11-04", "Ali", 5)));

        replies.put("GET /v4/spreadsheets/SHEET1/values/Sayfa1",
                new Reply(200, "{\"values\":[[\"ad\",\"puan\"],[\"Ali\",\"5\"],[\"Veli\"]]}"));
        StepLog read = run("googleSheets", Map.of("credential", cred, "operation", "read",
                "spreadsheetId", "SHEET1", "range", "Sayfa1"), Map.of());
        assertThat(read.status()).isEqualTo("SUCCESS");
        assertThat(read.output()).containsEntry("count", 2);
        assertThat((List<?>) read.output().get("rows")).first().isEqualTo(Map.of("ad", "Ali", "puan", "5"));
        assertThat(requests.stream().filter(x -> x.path().equals("/token")).count()).isEqualTo(1);
    }

    @Autowired VariableService variables;

    @Test
    void googleSheetsExamplesAddTaskAndDigest() throws Exception {
        upsert("google", "google", Map.of("serviceAccountJson", serviceAccountJson(), "apiBase", base()));
        Optional<String> old = variables.get("sheetId");
        boolean inDb = variables.list().stream().anyMatch(r -> r.name().equals("sheetId") && r.fromDb());
        variables.save(inDb ? "sheetId" : null, "sheetId", "TASKS1", null);
        replies.put("POST /token", new Reply(200, "{\"access_token\":\"ya29.test\",\"expires_in\":3600}"));
        replies.put("POST /v4/spreadsheets/TASKS1/values/Tasks:append",
                new Reply(200, "{\"updates\":{\"updatedRange\":\"Tasks!A5:E5\"}}"));
        replies.put("GET /v4/spreadsheets/TASKS1/values/Tasks", new Reply(200, "{\"values\":["
                + "[\"created\",\"task\",\"owner\",\"due\",\"status\"],"
                + "[\"2026-11-01\",\"Prepare Q4 report\",\"Alice\",\"2026-11-20\",\"open\"],"
                + "[\"2026-11-02\",\"Renew SSL\",\"Bob\",\"2026-11-10\",\"done\"],"
                + "[\"2026-11-03\",\"Update docs\",\"Carol\",\"2026-11-30\",\"open\"]]}"));
        try {
            store.importJson(Files.readString(Path.of("examples/google-sheets.json")), true);

            Execution add = executions.run(store.find("sheets-add-task").orElseThrow(),
                    Map.of("task", "  Call supplier ", "due", "2026-11-12"), "webhook");
            assertThat(add.status()).isEqualTo("SUCCESS");
            assertThat(add.steps().get(add.steps().size() - 1).output())
                    .containsEntry("ok", true).containsEntry("range", "Tasks!A5:E5");
            Recorded append = requests.stream().filter(r -> r.path().endsWith(":append")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked")
            List<List<Object>> values = (List<List<Object>>) json(append.body()).get("values");
            assertThat(values.get(0)).containsExactly(LocalDate.now().toString(), "Call supplier",
                    "unassigned", "2026-11-12", "open");

            Execution digest = executions.run(store.find("sheets-open-tasks-digest").orElseThrow(), Map.of(), "schedule");
            assertThat(digest.status()).isEqualTo("SUCCESS");
            assertThat(digest.steps().get(digest.steps().size() - 1).output().get("logged")).isEqualTo(
                    "📋 2 open task(s) of 3:\n• Prepare Q4 report (Alice, due 2026-11-20)\n• Update docs (Carol, due 2026-11-30)");
        } finally {
            if (old.isPresent()) {
                variables.save("sheetId", "sheetId", old.get(), null);
            } else {
                variables.delete("sheetId");
            }
        }
    }

    @Test
    void googleSheetsBadServiceAccount() {
        String cred = credential("google", Map.of("serviceAccountJson", "{\"type\":\"service_account\"}"));
        StepLog s = run("googleSheets", Map.of("credential", cred, "spreadsheetId", "X", "values", "{{ input }}"),
                Map.of("a", 1));
        assertThat(s.status()).isEqualTo("ERROR");
        assertThat(s.error()).contains("client_email");
    }

    @Test
    void excelAndCsvFromRows() throws Exception {
        List<Map<String, Object>> rows = List.of(
                new LinkedHashMap<>(Map.of("id", 1, "musteri", "Ayşe Çelik", "tutar", 1250.5)),
                new LinkedHashMap<>(Map.of("id", 2, "musteri", "Şirket \"A\"; Ltd", "tutar", 99)));

        StepLog x = run("spreadsheet", Map.of("rows", "{{ input.rows }}", "fileName", "rapor",
                "columns", List.of("id", "musteri", "tutar")), Map.of("rows", rows));
        assertThat(x.status()).isEqualTo("SUCCESS");
        @SuppressWarnings("unchecked")
        Map<String, Object> file = (Map<String, Object>) x.output().get("file");
        assertThat(file).containsEntry("name", "rapor.xlsx");
        byte[] bytes = Base64.getDecoder().decode(String.valueOf(file.get("data")));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Report");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("musteri");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Ayşe Çelik");
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(1250.5);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
        }

        StepLog c = run("spreadsheet", Map.of("rows", "{{ input.rows }}", "format", "csv", "delimiter", ";",
                "columns", "id, musteri"), Map.of("rows", rows));
        @SuppressWarnings("unchecked")
        Map<String, Object> csvFile = (Map<String, Object>) c.output().get("file");
        String csv = new String(Base64.getDecoder().decode(String.valueOf(csvFile.get("data"))), StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("﻿id;musteri\r\n1;Ayşe Çelik\r\n2;\"Şirket \"\"A\"\"; Ltd\"\r\n");
    }

    @Test
    void emailAttachmentMustBeAFile() {
        String smtp = credential("smtp", Map.of("host", "127.0.0.1", "port", "1", "from", "a@b.com", "starttls", "false"));
        StepLog bad = run("email", Map.of("credential", smtp, "to", "x@y.com", "attachments", "metin"), Map.of());
        assertThat(bad.status()).isEqualTo("ERROR");
        assertThat(bad.error()).contains("file");
    }

    @Test
    void largeFilesAreNotStoredInHistory() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            rows.add(Map.of("id", i, "aciklama", UUID.randomUUID().toString()));
        }
        Workflow wf = new Workflow("buyuk-dosya", "x", List.of(
                new NodeDef("s", "trigger", Map.of()),
                new NodeDef("csv", "spreadsheet", Map.of("rows", "{{ input.rows }}", "format", "csv"))
        ), List.of(new Connection("s", "csv", null)));
        Execution ex = executions.run(wf, Map.of("rows", rows), "manual");
        assertThat(ex.status()).isEqualTo("SUCCESS");

        Execution stored = executionStore.find(ex.id()).orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> file = (Map<String, Object>) stored.steps().get(1).output().get("file");
        assertThat(String.valueOf(file.get("data"))).startsWith("…(").contains("not stored");
        assertThat(String.valueOf(stored.trigger().get("rows"))).isNotEmpty();
    }

    @Test
    void queueNodesReportConnectionErrors() {
        String redis = credential("redis", Map.of("host", "127.0.0.1", "port", "1", "database", "0", "ssl", "false"));
        StepLog r = run("redis", Map.of("credential", redis, "operation", "rpush", "key", "k", "message", "{{ input }}"),
                Map.of("a", 1));
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.error()).startsWith("Redis");

        String rabbit = credential("rabbitmq", Map.of("host", "127.0.0.1", "port", "1", "username", "guest",
                "password", "guest", "virtualHost", "/", "ssl", "false"));
        StepLog q = run("rabbitmq", Map.of("credential", rabbit, "routingKey", "siparisler", "message", "x"), Map.of());
        assertThat(q.status()).isEqualTo("ERROR");
        assertThat(q.error()).startsWith("RabbitMQ");

        assertThat(run("redis", Map.of("credential", redis, "operation", "yok"), Map.of()).error()).contains("operation");
    }

    @Test
    void kafkaSaslSettingsAndMessageEncoding() {
        Properties p = queues.kafka(Map.of("bootstrapServers", "k1:9092,k2:9092", "securityProtocol", "SASL_SSL",
                "saslMechanism", "SCRAM-SHA-512", "username", "kullanici", "password", "sif\"re"));
        assertThat(p.getProperty("bootstrap.servers")).isEqualTo("k1:9092,k2:9092");
        assertThat(p.getProperty("sasl.mechanism")).isEqualTo("SCRAM-SHA-512");
        assertThat(p.getProperty("sasl.jaas.config"))
                .startsWith("org.apache.kafka.common.security.scram.ScramLoginModule required")
                .contains("password=\"sif\\\"re\"");

        assertThat(queues.encode(Map.of("a", 1))).isEqualTo("{\"a\":1}");
        assertThat(queues.encode("düz metin")).isEqualTo("düz metin");
        assertThat(queues.decode("{\"a\":1}")).isEqualTo(Map.of("a", 1));
        assertThat(queues.decode("{bozuk")).isEqualTo("{bozuk");
    }

    @Test
    void queueListenerShowsErrorWhenBrokerIsUnreachableAndValidatesConfig() throws Exception {
        String rabbit = credential("rabbitmq", Map.of("host", "127.0.0.1", "port", "1", "username", "guest",
                "password", "guest", "virtualHost", "/", "ssl", "false"));
        Map<String, Object> listen = Map.of("type", "rabbitmq", "credential", rabbit, "queue", "siparisler");
        store.save(new Workflow("dinleyici-test", "Dinleyici", true, List.of(
                new NodeDef("s", "trigger", Map.of("listen", listen)),
                new NodeDef("l", "log", Map.of("message", "{{ trigger.body }}"))
        ), List.of(new Connection("s", "l", null))));

        assertThat(listeners.error("dinleyici-test")).isPresent();
        mvc.perform(get("/").param("q", "dinleyici-test"))
                .andExpect(content().string(containsString("rabbitmq · siparisler")))
                .andExpect(content().string(containsString("not connected")));

        store.setActive("dinleyici-test", false);
        assertThat(listeners.error("dinleyici-test")).isEmpty();
        store.delete("dinleyici-test");

        assertThatThrownBy(() -> store.save(new Workflow("dinleyici-gecersiz", "x", List.of(
                new NodeDef("s", "trigger", Map.of("listen", Map.of("type", "mqtt", "credential", "a")))), List.of())))
                .isInstanceOf(MiniFlowException.class).hasMessageContaining("mqtt");
        assertThatThrownBy(() -> store.save(new Workflow("dinleyici-eksik", "x", List.of(
                new NodeDef("s", "trigger", Map.of("listen", Map.of("type", "kafka", "credential", "a")))), List.of())))
                .isInstanceOf(MiniFlowException.class).hasMessageContaining("topic");
    }

    private static Map<String, Object> row(String workflow, int runs) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("workflow", workflow);
        row.put("runs", runs);
        return row;
    }
}