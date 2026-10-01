package br.com.telemicro.api.budget;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;

public record BudgetFilter(
        @Min(0) @Max(1000000) Integer page,
        @Min(1) @Max(100) Integer size,
        BudgetStatus status,
        @Size(max = 50) String serviceCode,
        @Size(max = 100) String q,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        Sort sort) {
    public enum Sort { NEWEST, OLDEST }
    public int pageNumber() { return page == null ? 0 : page; }
    public int pageSize() { return size == null ? 20 : size; }
    public Sort order() { return sort == null ? Sort.NEWEST : sort; }
}
