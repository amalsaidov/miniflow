package com.omernaci.miniflow.node.support;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;

public final class HttpSupport {

    private HttpSupport() {
    }

    public static RestClient client(RestClient.Builder builder, Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return builder.requestFactory(factory).build();
    }

    public static Response postJson(RestClient client, String url, Object body, Consumer<HttpHeaders> headers) {
        return sendJson(client, HttpMethod.POST, url, body, headers);
    }

    public static Response postForm(RestClient client, String url, Map<String, String> form) {
        StringJoiner body = new StringJoiner("&");
        form.forEach((k, v) -> body.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8)));
        try {
            ResponseEntity<String> r = client.post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body.toString())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(String.class);
            return new Response(r.getStatusCode().value(), r.getBody() == null ? "" : r.getBody());
        } catch (IllegalArgumentException e) {
            throw new MiniFlowException("error.http.badUrl");
        } catch (RestClientException e) {
            throw new MiniFlowException("error.http.io", host(url), rootMessage(e));
        }
    }

    public static Response sendJson(RestClient client, HttpMethod method, String url, Object body,
                                    Consumer<HttpHeaders> headers) {
        try {
            ResponseEntity<String> r = client.method(method)
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {  })
                    .toEntity(String.class);
            return new Response(r.getStatusCode().value(), r.getBody() == null ? "" : r.getBody());
        } catch (IllegalArgumentException e) {
            throw new MiniFlowException("error.http.badUrl");
        } catch (RestClientException e) {
            throw new MiniFlowException("error.http.io", host(url), rootMessage(e));
        }
    }

    public static Response get(RestClient client, String url, Consumer<HttpHeaders> headers) {
        try {
            ResponseEntity<String> r = client.get()
                    .uri(URI.create(url))
                    .headers(headers)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(String.class);
            return new Response(r.getStatusCode().value(), r.getBody() == null ? "" : r.getBody());
        } catch (IllegalArgumentException e) {
            throw new MiniFlowException("error.http.badUrl");
        } catch (RestClientException e) {
            throw new MiniFlowException("error.http.io", host(url), rootMessage(e));
        }
    }

    public record Response(int status, String body) {

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> json(ObjectMapper mapper) {
            try {
                Object v = mapper.readValue(body, Object.class);
                return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            } catch (Exception e) {
                return Map.of();
            }
        }

        public String shortBody() {
            String b = body.replaceAll("\\s+", " ").trim();
            return b.length() > 300 ? b.substring(0, 300) + "…" : b;
        }
    }

    private static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "?" : h;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        return m.replaceAll("https?://\\S+", "<url>");
    }
}