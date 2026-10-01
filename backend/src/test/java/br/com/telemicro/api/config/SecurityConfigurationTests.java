package br.com.telemicro.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SecurityConfigurationTests {
    @Test
    void missingInvalidAndShortJwtSecretsFailOutsideLocalProfile() {
        var config = new SecurityConfig();
        assertThatThrownBy(() -> config.jwtKey(properties("", 100), new MockEnvironment())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.jwtKey(properties("not-base64!", 100), new MockEnvironment())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.jwtKey(properties("c2hvcnQ=", 100), new MockEnvironment())).isInstanceOf(IllegalStateException.class);
        assertThat(config.jwtKey(properties("", 100), new MockEnvironment().withProperty("spring.profiles.active", "local")).getEncoded()).hasSize(32);
    }
    @Test
    void wildcardCorsConfigurationIsRejected() {
        var properties = new SecurityProperties("", "telemicro-api", "telemicro-admin", 1800,
                List.of("https://*.example.com"), 3, 3, 10, 100);
        assertThatThrownBy(() -> new SecurityConfig().corsConfigurationSource(properties)).isInstanceOf(IllegalStateException.class);
    }
    @Test
    void rateLimitExpiresAndMemoryBoundDoesNotEvictActiveBuckets() {
        var clock = new MutableClock();
        var limiter = new RequestRateLimiter(clock, properties("", 1));
        assertThat(limiter.retryAfter("first", 2)).isZero();
        assertThat(limiter.retryAfter("first", 2)).isZero();
        assertThat(limiter.retryAfter("first", 2)).isEqualTo(10);
        assertThat(limiter.retryAfter("another", 2)).isEqualTo(10);
        assertThat(limiter.retryAfter("first", 2)).isEqualTo(10);
        clock.now = clock.now.plusSeconds(10);
        assertThat(limiter.retryAfter("another", 2)).isZero();
    }
    private SecurityProperties properties(String key, int capacity) {
        return new SecurityProperties(key, "telemicro-api", "telemicro-admin", 1800, List.of(), 3, 3, 10, capacity);
    }
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
