package br.com.telemicro.api.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(
        @DefaultValue("") String jwtSecret,
        @DefaultValue("telemicro-api") @NotBlank String issuer,
        @DefaultValue("telemicro-admin") @NotBlank String audience,
        @DefaultValue("1800") @Min(60) @Max(3600) long tokenTtlSeconds,
        @DefaultValue List<String> allowedOrigins,
        @DefaultValue("10") @Min(1) @Max(10000) int loginLimit,
        @DefaultValue("20") @Min(1) @Max(10000) int submissionLimit,
        @DefaultValue("900") @Min(1) @Max(86400) int rateWindowSeconds,
        @DefaultValue("10000") @Min(1) @Max(100000) int maxRateKeys) {}
