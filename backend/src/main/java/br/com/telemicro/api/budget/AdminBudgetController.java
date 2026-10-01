package br.com.telemicro.api.budget;

import static br.com.telemicro.api.budget.AdminBudgetModels.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/budgets")
public class AdminBudgetController {
    private final AdminBudgetService service;
    public AdminBudgetController(AdminBudgetService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<BudgetPage> list(@Valid @ModelAttribute BudgetFilter filter) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(filter));
    }
    @GetMapping("/summary")
    public ResponseEntity<Summary> summary(@Valid @ModelAttribute BudgetFilter filter) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.summary(filter));
    }
    @GetMapping("/{id}")
    public ResponseEntity<BudgetDetails> details(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.details(id));
    }
    @PatchMapping("/{id}/status")
    public ResponseEntity<BudgetItem> changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.changeStatus(id, request, UUID.fromString(jwt.getSubject())));
    }
    @PostMapping("/{id}/notes")
    public ResponseEntity<NoteItem> addNote(@PathVariable UUID id, @Valid @RequestBody CreateNoteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.addNote(id, request, UUID.fromString(jwt.getSubject())));
    }
}
