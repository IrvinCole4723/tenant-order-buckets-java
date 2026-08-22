package learning.store.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infrai")
public record InfraiStorageProperties(String baseUrl, String apiKey, int receiptExpirySeconds) {
    public InfraiStorageProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("INFRAI_API_KEY must be set");
        }
    }
}
