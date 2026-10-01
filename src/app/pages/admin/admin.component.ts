import { Component, DestroyRef, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, finalize, forkJoin, timeout } from 'rxjs';
import { API_URL, BudgetDetails, BudgetPage, ServiceOption, Status, STATUS_LABELS, apiError } from '../../core/api';
import { AuthService } from '../../core/auth.service';
@Component({ standalone: true, imports: [ReactiveFormsModule, DatePipe, RouterLink], templateUrl: './admin.component.html', styleUrl: './admin.css' })
export class AdminComponent {
  readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);
  private readonly destroy = inject(DestroyRef);
  readonly labels = STATUS_LABELS;
  readonly statuses = Object.keys(STATUS_LABELS) as Status[];
  readonly filters = inject(FormBuilder).nonNullable.group({ q: '', status: '', serviceCode: '', from: '', to: '', sort: 'NEWEST' });
  readonly edit = inject(FormBuilder).nonNullable.group({ status: '', justification: '', note: '' });
  services: ServiceOption[] = []; page?: BudgetPage; details?: BudgetDetails;
  counts: Partial<Record<Status, number>> = {}; total = 0;
  serviceCounts: { code: string; name: string; total: number }[] = [];
  busy = false; detailBusy = false; saving = false; error = ''; detailError = ''; notice = '';
  private listRequest?: Subscription;
  private detailRequest?: Subscription;
  constructor() {
    this.http.get<ServiceOption[]>(`${this.api}/services`).pipe(timeout(90000), takeUntilDestroyed(this.destroy)).subscribe({ next: s => this.services = s, error: () => this.error = 'Não foi possível carregar os serviços. Atualize a página para tentar novamente.' });
    this.load();
  }
  load(page = 0): void {
    this.listRequest?.unsubscribe();
    this.error = '';
    const f = this.filters.getRawValue();
    if (f.from && f.to && f.from > f.to) { this.error = 'A data inicial deve ser anterior à data final.'; return; }
    let params = new HttpParams().set('page', page).set('size', 20);
    for (const [key, value] of Object.entries(f)) {
      if (!value) continue;
      if (key === 'from' || key === 'to') {
        const date = new Date(`${value}T00:00:00`);
        if (key === 'to') date.setDate(date.getDate() + 1);
        params = params.set(key, date.toISOString());
      } else params = params.set(key, value.trim());
    }
    this.busy = true;
    this.listRequest = forkJoin({ page: this.http.get<BudgetPage>(`${this.api}/admin/budgets`, { params }), summary: this.http.get<{ counts: Record<Status, number>; total: number; services?: { code: string; name: string; total: number }[] }>(`${this.api}/admin/budgets/summary`, { params }) }).pipe(timeout(90000), takeUntilDestroyed(this.destroy), finalize(() => this.busy = false)).subscribe({
      next: r => { this.page = r.page; this.counts = r.summary.counts; this.total = r.summary.total; this.serviceCounts = r.summary.services ?? []; }, error: e => { this.serviceCounts = []; this.page = undefined; this.counts = {}; this.total = 0; this.error = apiError(e); }
    });
  }
  percent(count: number): number { return this.total ? Math.round(count / this.total * 100) : 0; }
  serviceWidth(count: number): number { return this.serviceCounts[0]?.total ? count / this.serviceCounts[0].total * 100 : 0; }
  get statusGradient(): string {
    const colors = ['#3b82f6', '#f59e0b', '#10b981', '#94a3b8'];
    let position = 0;
    return 'conic-gradient(' + this.statuses.map((status, index) => {
      const start = position; position += this.total ? (this.counts[status] ?? 0) / this.total * 100 : 0;
      return `${colors[index]} ${start}% ${position}%`;
    }).join(', ') + ')';
  }
  filterStatus(status: Status): void { this.filters.controls.status.setValue(this.filters.controls.status.value === status ? '' : status); this.load(); }
  filterService(code: string): void { this.filters.controls.serviceCode.setValue(code); this.load(); }
  resetFilters(): void { this.filters.reset({ q: '', status: '', serviceCode: '', from: '', to: '', sort: 'NEWEST' }); this.load(); }
  open(id: string, preserveMessage = false): void {
    this.detailRequest?.unsubscribe();
    this.details = undefined; this.detailBusy = true;
    if (!preserveMessage) { this.detailError = ''; this.notice = ''; this.edit.reset(); }
    this.detailRequest = this.http.get<BudgetDetails>(`${this.api}/admin/budgets/${id}`).pipe(timeout(90000), takeUntilDestroyed(this.destroy), finalize(() => this.detailBusy = false)).subscribe({
      next: d => { this.details = d; this.edit.controls.status.setValue(''); }, error: e => this.detailError = apiError(e)
    });
  }
  close(): void { this.detailRequest?.unsubscribe(); this.details = undefined; this.detailBusy = false; this.detailError = ''; this.notice = ''; }
  transitions(): Status[] {
    const s = this.details?.budget.status;
    return s === 'NEW' ? ['IN_PROGRESS', 'CANCELLED'] : s === 'IN_PROGRESS' ? ['COMPLETED', 'CANCELLED'] : s ? ['IN_PROGRESS'] : [];
  }
  changeStatus(): void {
    const d = this.details; if (!d || this.saving || this.auth.user()?.role !== 'ADMIN') return;
    const { status, justification } = this.edit.getRawValue();
    if (!this.transitions().includes(status as Status)) { this.detailError = 'Selecione o novo status.'; return; }
    if (['COMPLETED', 'CANCELLED'].includes(d.budget.status) && !justification.trim()) { this.detailError = 'Informe o motivo da reabertura.'; return; }
    this.mutate(this.http.patch(`${this.api}/admin/budgets/${d.budget.id}/status`, { status, version: d.budget.version, justification: justification.trim() }), d.budget.id, 'Status atualizado.');
  }
  addNote(): void {
    const d = this.details; if (!d || this.saving || this.auth.user()?.role !== 'ADMIN') return;
    const text = this.edit.controls.note.value.trim();
    if (!text) { this.detailError = 'Escreva a observação.'; return; }
    this.mutate(this.http.post(`${this.api}/admin/budgets/${d.budget.id}/notes`, { text }), d.budget.id, 'Observação adicionada.');
  }
  private mutate(request: ReturnType<HttpClient['post']>, id: string, message: string): void {
    this.saving = true; this.detailError = ''; this.notice = '';
    request.pipe(timeout(90000), takeUntilDestroyed(this.destroy), finalize(() => this.saving = false)).subscribe({
      next: () => { this.edit.reset(); this.notice = message; this.open(id, true); this.load(this.page?.page ?? 0); },
      error: e => { this.detailError = e.status === 0 || e.name === 'TimeoutError' ? 'Não foi possível confirmar a alteração. Atualize os detalhes antes de repetir a ação.' : apiError(e); if (e.status === 409) { this.open(id, true); this.load(this.page?.page ?? 0); } }
    });
  }
}
