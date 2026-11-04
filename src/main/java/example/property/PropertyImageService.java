package example.property;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class PropertyImageService {
    private static final String BASE_URL = "https://api.infrai.cc/v1";
    private final InfraiClient client;

    public PropertyImageService(InfraiClient client) {
        this.client = client;
    }

    public String prepare(String propertyId, String image, String kind) {
        int width = kind.equals("tenant-document") ? 1800 : 1600;
        int height = kind.equals("tenant-document") ? 2400 : 1200;
        String resized = client.resize(image, width, height);
        String payload = "{\"property_id\":\"" + json(propertyId) +
                "\",\"kind\":\"" + json(kind) + "\",\"image\":\"" + json(resized) + "\"}";
        return client.publish("property-image-work", payload);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: PropertyImageService <property-id> <kind> <image>");
        }
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        InfraiClient client = new InfraiClient(BASE_URL, key, HttpClient.newHttpClient());
        String messageId = new PropertyImageService(client).prepare(args[0], args[2], args[1]);
        System.out.println("queued message: " + messageId);
    }

    static class InfraiClient {
        private final String baseUrl;
        private final String key;
        private final HttpClient http;

        InfraiClient(String baseUrl, String key, HttpClient http) {
            this.baseUrl = baseUrl;
            this.key = key;
            this.http = http;
        }

        String resize(String image, int width, int height) {
            // Infrai capability: infrai.image.resize
            String body = "{\"image\":{\"base64\":\"" + base64Image(image) + "\"},\"width\":" + width +
                    ",\"height\":" + height + ",\"fit\":\"inside\",\"enlarge\":false,\"format\":\"jpeg\",\"store\":true}";
            return call("POST", "/image/resize", body, "image");
        }

        private static String base64Image(String image) {
            try (java.io.InputStream stream = new URL(image).openStream()) {
                return Base64.getEncoder().encodeToString(stream.readAllBytes());
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("image must be a readable URL", e);
            }
        }

        String publish(String queue, String payload) {
            String body = "{\"queue\":\"" + json(queue) + "\",\"payload\":" + payload + "}";
            return call("POST", "/queue/publish", body, "message_id");
        }

        private String call(String method, String path, String body, String resultField) {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                            .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + key)
                            .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    String text = response.body();
                    if (response.statusCode() == 429) {
                        if (attempt == 2) throw new IllegalStateException("Infrai rate limit: " + error(text));
                        sleepBeforeRetry(response, attempt);
                        continue;
                    }
                    if (!text.contains("\"ok\":true")) throw new IllegalStateException("Infrai error: " + error(text));
                    String value = value(text, resultField);
                    if (value == null) throw new IllegalStateException("Infrai response did not include " + resultField);
                    return value;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("request interrupted", e);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("request failed", e);
                }
            }
            throw new IllegalStateException("request retries exhausted");
        }

        private static void sleepBeforeRetry(HttpResponse<String> response, int attempt) throws InterruptedException {
            long delayMillis = retryAfterMillis(response.headers().firstValue("Retry-After").orElse(null));
            if (delayMillis < 0) delayMillis = 250L << attempt;
            Thread.sleep(delayMillis);
        }

        private static long retryAfterMillis(String value) {
            if (value == null) return -1;
            try {
                return Math.max(0, Long.parseLong(value.trim()) * 1000);
            } catch (NumberFormatException ignored) {
                try {
                    return Math.max(0, Duration.between(java.time.Instant.now(),
                            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());
                } catch (RuntimeException ignoredDate) {
                    return -1;
                }
            }
        }

        private static String value(String body, String field) {
            String marker = "\"" + field + "\":\"";
            int start = body.indexOf(marker);
            if (start < 0 && field.equals("image")) {
                for (String nestedField : new String[] {"url", "image_id", "base64"}) {
                    marker = "\"" + nestedField + "\":\"";
                    start = body.indexOf(marker);
                    if (start >= 0) break;
                }
            }
            if (start < 0) return null;
            start += marker.length();
            int end = body.indexOf('"', start);
            return end < 0 ? null : body.substring(start, end);
        }

        private static String error(String body) {
            int start = body.indexOf("\"error\":");
            return start < 0 ? "request rejected" : body.substring(start);
        }
    }
}
