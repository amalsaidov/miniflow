package com.omernaci.miniflow.node.google;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.node.support.HttpSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GoogleAuth {

    public static final String SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets";

    private final RestClient client;
    private final ObjectMapper mapper;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    private record CachedToken(String token, Instant expiresAt) {
    }

    record ServiceAccount(String clientEmail, String privateKeyPem, String tokenUri) {
    }

    public GoogleAuth(RestClient.Builder builder, ObjectMapper mapper) {
        this.client = HttpSupport.client(builder, Duration.ofSeconds(30));
        this.mapper = mapper;
    }

    public String accessToken(String serviceAccountJson, String scope) {
        ServiceAccount sa = parse(serviceAccountJson);
        String cacheKey = sa.clientEmail() + "|" + scope + "|" + Integer.toHexString(serviceAccountJson.hashCode());
        CachedToken cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cached.token();
        }

        String assertion = jwt(sa, scope);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.put("assertion", assertion);
        HttpSupport.Response r = HttpSupport.postForm(client, sa.tokenUri(), form);
        Map<String, Object> json = r.json(mapper);
        if (!r.ok() || json.get("access_token") == null) {
            Object err = json.getOrDefault("error_description", json.getOrDefault("error", r.shortBody()));
            throw new MiniFlowException("error.google.auth", r.status(), err);
        }
        long expiresIn = json.get("expires_in") instanceof Number n ? n.longValue() : 3600;
        String token = String.valueOf(json.get("access_token"));
        cache.put(cacheKey, new CachedToken(token, Instant.now().plusSeconds(expiresIn)));
        return token;
    }

    ServiceAccount parse(String json) {
        try {
            Map<?, ?> m = mapper.readValue(json, Map.class);
            Object email = m.get("client_email");
            Object key = m.get("private_key");
            if (email == null || key == null) {
                throw new MiniFlowException("error.google.serviceAccount");
            }
            Object tokenUri = m.get("token_uri");
            return new ServiceAccount(String.valueOf(email), String.valueOf(key),
                    tokenUri == null ? "https://oauth2.googleapis.com/token" : String.valueOf(tokenUri));
        } catch (MiniFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new MiniFlowException("error.google.serviceAccount");
        }
    }

    String jwt(ServiceAccount sa, String scope) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", sa.clientEmail());
        claims.put("scope", scope);
        claims.put("aud", sa.tokenUri());
        claims.put("iat", now);
        claims.put("exp", now + 3600);
        try {
            Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
            String signingInput = b64.encodeToString(mapper.writeValueAsBytes(header)) + "."
                    + b64.encodeToString(mapper.writeValueAsBytes(claims));
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey(sa.privateKeyPem()));
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64.encodeToString(signer.sign());
        } catch (MiniFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new MiniFlowException(e, "error.google.serviceAccount");
        }
    }

    private static PrivateKey privateKey(String pem) {
        try {
            String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\n", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new MiniFlowException(e, "error.google.serviceAccount");
        }
    }
}
