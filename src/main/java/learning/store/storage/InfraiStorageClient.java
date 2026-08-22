package learning.store.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import learning.store.config.InfraiStorageProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public final class InfraiStorageClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    private final InfraiStorageProperties properties;

    public InfraiStorageClient(ObjectMapper json, InfraiStorageProperties properties) {
        this.json = json;
        this.properties = properties;
    }

    public void ensureBucket(String bucket) {
        try {
            call("GET", "/v1/storage/bucket/get/" + segment(bucket), null);
        } catch (InfraiException error) {
            if (error.status() != 404) {
                throw error;
            }
            call("POST", "/v1/storage/bucket/create", Map.of("name", bucket));
        }
    }

    public boolean objectExists(String bucket, String key) {
        JsonNode data = call("GET", "/v1/storage/object/head/" + segment(bucket) + "/" + keyPath(key), null);
        return data.path("found").asBoolean(false);
    }

    public void putJson(String bucket, String key, String jsonDocument, String idempotencyKey) {
        String encoded = java.util.Base64.getEncoder().encodeToString(jsonDocument.getBytes(StandardCharsets.UTF_8));
        call("PUT", "/v1/storage/object/put/" + segment(bucket) + "/" + keyPath(key), Map.of(
                "data_base64", encoded,
                "content_type", "application/json",
                "idempotency_key", idempotencyKey));
    }

    public String presignReceipt(String bucket, String key, int expiresSeconds) {
        JsonNode data = call("POST", "/v1/storage/object/presign/" + segment(bucket) + "/" + keyPath(key), Map.of(
                "op", "get",
                "expires_seconds", expiresSeconds,
                "response_disposition", "attachment"));
        return data.path("url").asText();
    }

    private JsonNode call(String method, String path, Object body) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpResponse<String> response = send(method, path, body);
            JsonNode envelope = decodeEnvelope(response.body(), response.statusCode());
            if (response.statusCode() == 429 && attempt < 3) {
                pause(retryDelayMillis(response, attempt));
                continue;
            }
            if (!envelope.path("ok").asBoolean(false)) {
                JsonNode error = envelope.path("error");
                throw new InfraiException(error.path("code").asText("INFRAI_ERROR"),
                        error.path("message").asText("Infrai request rejected"), response.statusCode());
            }
            return envelope.path("data");
        }
        throw new IllegalStateException("Retry loop exhausted");
    }

    private HttpResponse<String> send(String method, String path, Object body) {
        try {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, publisher)
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not complete storage request", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Storage request interrupted", exception);
        }
    }

    private JsonNode decodeEnvelope(String body, int status) {
        try {
            return json.readTree(body);
        } catch (IOException exception) {
            throw new InfraiException("INVALID_ENVELOPE", "Storage returned an unreadable response", status);
        }
    }

    private long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Long.parseLong(value) * 1_000L;
                    } catch (NumberFormatException ignored) {
                        return 250L * (1L << attempt);
                    }
                })
                .orElse(250L * (1L << attempt));
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", exception);
        }
    }

    private String keyPath(String key) {
        return java.util.Arrays.stream(key.split("/", -1)).map(this::segment).reduce((a, b) -> a + "/" + b).orElse("");
    }

    private String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
