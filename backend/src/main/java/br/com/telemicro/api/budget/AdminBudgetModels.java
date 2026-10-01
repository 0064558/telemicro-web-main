package br.com.telemicro.api.budget;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class AdminBudgetModels {
    private AdminBudgetModels() {}
    public record BudgetItem(UUID id, String protocol, String customerName, String phone, String serviceCode,
                             String serviceName, String message, BudgetStatus status, long version,
                             Instant createdAt, Instant updatedAt) {}
    public record BudgetPage(List<BudgetItem> content, int page, int size, long totalElements, long totalPages) {}
    public record HistoryItem(UUID id, BudgetStatus previousStatus, BudgetStatus newStatus,
                              UUID changedBy, String changedByEmail, Instant createdAt) {}
    public record NoteItem(UUID id, UUID authorId, String authorEmail, String text, Instant createdAt) {}
    public record BudgetDetails(BudgetItem budget, List<HistoryItem> history, List<NoteItem> notes) {}
    public record ServiceCount(String code, String name, long total) {}
    public record Summary(Map<BudgetStatus, Long> counts, long total, List<ServiceCount> services) {}
    public record ChangeStatusRequest(@NotNull(message = "Informe o status.") BudgetStatus status,
                                      @NotNull(message = "Informe a versão atual do pedido.") @Min(0) Long version,
                                      @Size(max = 2000) String justification) {}
    public record CreateNoteRequest(@NotBlank(message = "Informe a observação.") @Size(max = 2000) String text) {}
}
