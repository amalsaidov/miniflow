package com.omernaci.miniflow.node.google;

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
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class GoogleSheetsNode implements NodeExecutor {

    private final CredentialService credentials;
    private final GoogleAuth auth;
    private final RestClient client;
    private final ObjectMapper mapper;

    public GoogleSheetsNode(CredentialService credentials, GoogleAuth auth, RestClient.Builder builder, ObjectMapper mapper) {
        this.credentials = credentials;
        this.auth = auth;
        this.client = HttpSupport.client(builder, Duration.ofSeconds(30));
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "googleSheets";
    }

    @Override
    public String label() {
        return "Google Sheets";
    }

    @Override
    public List<String> credentialTypes() {
        return List.of("google");
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("credential", "");
        p.put("operation", "append");
        p.put("spreadsheetId", "{{ vars.sheetId }}");
        p.put("range", "Sheet1");
        p.put("values", "{{ input }}");
        return p;
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.credential(),
                ParamField.select("operation", "append", "read").mandatory(),
                ParamField.text("spreadsheetId").mandatory().placeholder("{{ vars.sheetId }}"),
                ParamField.text("range").mandatory().placeholder("Sheet1"),
                ParamField.json("values").mandatory().when("operation", "append"),
                ParamField.list("columns").when("operation", "append"));
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        Map<String, String> c = credentials.require(Params.str(params, "credential"), "google");
        String api = c.getOrDefault("apiBase", "").isBlank() ? "https://sheets.googleapis.com"
                : c.get("apiBase").replaceAll("/+$", "");
        String sheetId = Params.required(params, "spreadsheetId").trim();
        if (!sheetId.matches("[A-Za-z0-9_-]+")) {
            throw new MiniFlowException("error.sheets.id", sheetId);
        }
        String range = Params.str(params, "range", "Sheet1").trim();
        String url = api + "/v4/spreadsheets/" + sheetId + "/values/" + UriUtils.encodePathSegment(range, StandardCharsets.UTF_8);

        String token = auth.accessToken(c.get("serviceAccountJson"), GoogleAuth.SHEETS_SCOPE);
        Consumer<HttpHeaders> headers = h -> h.setBearerAuth(token);
        String op = Params.str(params, "operation", "append").trim();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operation", op);
        switch (op) {
            case "append" -> {
                List<List<Object>> rows = rows(params.get("values"), params.get("columns"));
                if (rows.isEmpty()) {
                    throw new MiniFlowException("error.param.required", "values");
                }
                Map<String, Object> json = call(HttpMethod.POST,
                        url + ":append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS",
                        Map.of("values", rows), headers);
                Object updates = json.get("updates");
                out.put("appendedRows", rows.size());
                if (updates instanceof Map<?, ?> u) {
                    out.put("updatedRange", u.get("updatedRange"));
                }
            }
            case "read" -> {
                HttpSupport.Response r = HttpSupport.get(client, url, headers);
                Map<String, Object> json = check(r);
                List<List<Object>> values = new ArrayList<>();
                if (json.get("values") instanceof List<?> l) {
                    l.forEach(row -> values.add(row instanceof List<?> cells ? new ArrayList<>(cells) : List.of(row)));
                }
                List<Map<String, Object>> objects = new ArrayList<>();
                if (values.size() > 1) {
                    List<Object> header = values.get(0);
                    for (List<Object> row : values.subList(1, values.size())) {
                        Map<String, Object> o = new LinkedHashMap<>();
                        for (int i = 0; i < header.size(); i++) {
                            o.put(String.valueOf(header.get(i)), i < row.size() ? row.get(i) : null);
                        }
                        objects.add(o);
                    }
                }
                out.put("values", values);
                out.put("rows", objects);
                out.put("count", objects.size());
            }
            default -> throw new MiniFlowException("error.param.oneOf", "operation", "append, read");
        }
        return NodeResult.of(out);
    }

    private Map<String, Object> call(HttpMethod method, String url, Object body, Consumer<HttpHeaders> headers) {
        return check(HttpSupport.sendJson(client, method, url, body, headers));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> check(HttpSupport.Response r) {
        Map<String, Object> json = r.json(mapper);
        if (!r.ok()) {
            Object message = json.get("error") instanceof Map<?, ?> err && err.get("message") != null
                    ? ((Map<String, Object>) err).get("message") : r.shortBody();
            throw new MiniFlowException("error.sheets", r.status(), message);
        }
        return json;
    }

    @SuppressWarnings("unchecked")
    List<List<Object>> rows(Object values, Object columnsParam) {
        List<Object> items;
        if (values == null || (values instanceof String s && s.isBlank())) {
            return List.of();
        } else if (values instanceof Map<?, ?> single) {
            items = List.of(single);
        } else if (values instanceof Collection<?> list) {
            items = new ArrayList<>(list);
        } else {
            items = List.of(List.of(values));
        }
        List<String> columns = new ArrayList<>(Params.stringList(columnsParam));
        if (columns.isEmpty()) {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            items.stream().filter(i -> i instanceof Map<?, ?>).findFirst()
                    .ifPresent(first -> ((Map<?, ?>) first).keySet().forEach(k -> keys.add(String.valueOf(k))));
            columns.addAll(keys);
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Object item : items) {
            List<Object> row = new ArrayList<>();
            if (item instanceof Map<?, ?> m) {
                for (String col : columns) {
                    row.add(cell(((Map<String, Object>) m).get(col)));
                }
            } else if (item instanceof Collection<?> cells) {
                cells.forEach(v -> row.add(cell(v)));
            } else {
                row.add(cell(item));
            }
            rows.add(row);
        }
        return rows;
    }

    private Object cell(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Number || v instanceof Boolean || v instanceof String) {
            return v;
        }
        if (v instanceof Map<?, ?> || v instanceof Collection<?>) {
            try {
                return mapper.writeValueAsString(v);
            } catch (Exception e) {
                return String.valueOf(v);
            }
        }
        return String.valueOf(v);
    }
}
