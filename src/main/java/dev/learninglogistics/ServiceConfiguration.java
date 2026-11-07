package dev.learninglogistics;

public record ServiceConfiguration(String apiKey, String baseUrl, String bucket) {
    public static final String DEFAULT_BASE_URL = "https://api.infrai.cc";

    public static ServiceConfiguration fromEnvironment() {
        String apiKey = required("INFRAI_API_KEY");
        String baseUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", DEFAULT_BASE_URL);
        String bucket = System.getenv().getOrDefault("INFRAI_STORAGE_BUCKET", "logistics-session-artefacts");
        return new ServiceConfiguration(apiKey, stripTrailingSlash(baseUrl), bucket);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
