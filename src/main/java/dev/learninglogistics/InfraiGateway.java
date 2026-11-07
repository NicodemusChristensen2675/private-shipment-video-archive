package dev.learninglogistics;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class InfraiGateway {
    private final HttpClient http;
    private final ServiceConfiguration configuration;

    public InfraiGateway(ServiceConfiguration configuration) {
        this(configuration, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiGateway(ServiceConfiguration configuration, HttpClient http) {
        this.configuration = configuration;
        this.http = http;
    }

    public Map<String, Object> createBucket(String name) {
        return request("POST", "/v1/storage/bucket/create", Map.of("name", name));
    }

    public Map<String, Object> createRoom(String name) {
        return request("POST", "/v1/rtc/room/create", Map.of(
                "name", name,
                "max_participants", 6,
                "empty_timeout_s", 900,
                "region", "auto"));
    }

    public Map<String, Object> issueRoomToken(String room, String identity, String displayName) {
        return request("POST", "/v1/rtc/token/issue", Map.of(
                "room", room,
                "identity", identity,
                "display_name", displayName,
                "ttl_s", 3600,
                "can_publish", true,
                "can_subscribe", true));
    }

    public Map<String, Object> presignArtefactUpload(
            String bucket, String key, String contentType, long maxBytes, String idempotencyKey) {
        String path = "/v1/storage/object/presign/" + segment(bucket) + "/" + pathKey(key);
        return request("POST", path, Map.of(
                "op", "put",
                "expires_seconds", 900,
                "content_type", contentType,
                "max_bytes", maxBytes,
                "idempotency_key", idempotencyKey));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String method, String path, Map<String, Object> body) {
        String json = Json.stringify(body);
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(configuration.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + configuration.apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Object decoded = Json.parse(response.body());
                if (!(decoded instanceof Map<?, ?> rawEnvelope)) {
                    throw new IllegalStateException("Infrai returned a non-object envelope");
                }
                Map<String, Object> envelope = (Map<String, Object>) rawEnvelope;
                if (response.statusCode() == 429 && attempt < 3) {
                    sleep(backoffMillis(response, attempt));
                    continue;
                }
                if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                    Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> value
                            ? (Map<String, Object>) value : Map.of();
                    String code = String.valueOf(error.getOrDefault("code", "INFRAI_REQUEST_REJECTED"));
                    String message = String.valueOf(error.getOrDefault("message", code));
                    throw new InfraiException(code, message, response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new IllegalStateException("Infrai transport response: HTTP " + response.statusCode());
                }
                Object data = envelope.get("data");
                return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of("value", data);
            } catch (IOException e) {
                throw new IllegalStateException("Could not reach Infrai", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while calling Infrai", e);
            }
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static long backoffMillis(HttpResponse<?> response, int attempt) {
        List<String> values = response.headers().allValues("Retry-After");
        if (!values.isEmpty()) {
            try { return Math.max(0, Long.parseLong(values.get(0))) * 1000L; }
            catch (NumberFormatException ignored) { }
        }
        return 250L * (1L << attempt);
    }

    private static void sleep(long millis) throws InterruptedException { Thread.sleep(millis); }
    private static String segment(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String pathKey(String key) {
        return String.join("/", java.util.Arrays.stream(key.split("/", -1)).map(InfraiGateway::segment).toList());
    }
}
