package br.com.telemicro.api.budget;

import java.time.Instant;

public record BudgetCreatedResponse(String protocol, Instant createdAt) {}
