package br.com.telemicro.api.config;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Component
public class RequestRateLimiter {
    private final Clock clock;
    private final SecurityProperties properties;
    private final Map<String, Bucket> buckets = new HashMap<>();

    public RequestRateLimiter(Clock clock, SecurityProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public synchronized long retryAfter(String key, int limit) {
        long now = clock.instant().getEpochSecond();
        Bucket bucket = buckets.get(key);
        if (bucket == null || bucket.expiresAt <= now) {
            if (bucket == null && buckets.size() >= properties.maxRateKeys()) {
                buckets.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
                if (buckets.size() >= properties.maxRateKeys()) return properties.rateWindowSeconds();
            }
            buckets.put(key, new Bucket(1, now + properties.rateWindowSeconds()));
            return 0;
        }
        if (bucket.count >= limit) return Math.max(1, bucket.expiresAt - now);
        bucket.count++;
        return 0;
    }

    private static class Bucket {
        int count;
        final long expiresAt;
        Bucket(int count, long expiresAt) { this.count = count; this.expiresAt = expiresAt; }
    }
}
