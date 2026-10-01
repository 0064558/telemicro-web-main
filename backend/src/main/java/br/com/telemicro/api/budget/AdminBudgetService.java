package br.com.telemicro.api.budget;

import static br.com.telemicro.api.budget.AdminBudgetModels.*;
import br.com.telemicro.api.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'DEMO')")
public class AdminBudgetService {
    private final AdminBudgetRepository repository;
    private final Clock clock;
    public AdminBudgetService(AdminBudgetRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BudgetPage list(BudgetFilter filter) { validateDates(filter); return repository.list(filter, demoOnly()); }
    @Transactional(readOnly = true)
    public Summary summary(BudgetFilter filter) { validateDates(filter); return repository.summary(filter, demoOnly()); }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BudgetDetails details(UUID id) {
        BudgetItem budget = find(id, demoOnly(), false);
        return new BudgetDetails(budget, repository.history(id), repository.notes(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public BudgetItem changeStatus(UUID id, ChangeStatusRequest request, UUID actor) {
        BudgetItem budget = find(id, false, true);
        if (budget.version() != request.version()) throw new ApiException(HttpStatus.CONFLICT, "O pedido foi atualizado. Recarregue os dados antes de alterar.");
        if (budget.status() == request.status()) return budget;
        if (!budget.status().canChangeTo(request.status())) throw new ApiException(HttpStatus.CONFLICT, "Esta mudança de status não é permitida.");
        String reason = request.justification() == null ? "" : request.justification().strip();
        if (budget.status().isClosed() && reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe o motivo para reabrir o atendimento.", "justification");
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (repository.updateStatus(id, budget.version(), request.status(), now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "O pedido foi atualizado. Recarregue os dados antes de alterar.");
        }
        repository.addHistory(id, budget.status(), request.status(), actor, now);
        if (!reason.isBlank()) repository.addNote(id, actor, reason, now);
        return find(id, false, false);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public NoteItem addNote(UUID id, CreateNoteRequest request, UUID actor) {
        find(id, false, true);
        String text = request.text().strip();
        if (text.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Informe a observação.", "text");
        UUID noteId = repository.addNote(id, actor, text, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return repository.notes(id).stream().filter(note -> note.id().equals(noteId)).findFirst().orElseThrow();
    }

    private BudgetItem find(UUID id, boolean demoOnly, boolean lock) {
        return repository.find(id, demoOnly, lock).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    }
    private boolean demoOnly() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DEMO"));
    }
    private void validateDates(BudgetFilter filter) {
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A data inicial deve ser anterior à data final.", "from");
        }
    }
}
