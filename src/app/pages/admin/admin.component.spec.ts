import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AdminComponent } from './admin.component';
import { AuthService } from '../../core/auth.service';
import { BudgetDetails } from '../../core/api';
describe('Admin panel', () => {
  let component: AdminComponent; let http: HttpTestingController;
  const details: BudgetDetails = { budget: { id: 'budget-1', protocol: 'TM-TEST', customerName: 'Teste', phone: '5533999999999', serviceCode: 'OTHER', serviceName: 'Outro', message: '', status: 'COMPLETED', version: 3, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }, history: [], notes: [] };
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AdminComponent], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    component = TestBed.createComponent(AdminComponent).componentInstance;
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).user.set({ id: 'admin', email: 'admin@example.test', role: 'ADMIN' });
    http.expectOne('/api/v1/services').flush([]);
    http.expectOne(r => r.url === '/api/v1/admin/budgets').flush({ content: [], page: 0, totalPages: 0, totalElements: 0 });
    http.expectOne(r => r.url === '/api/v1/admin/budgets/summary').flush({ counts: {}, total: 0 });
    component.details = structuredClone(details);
  });
  afterEach(() => http.verify());
  it('uses aggregate service totals rather than the visible page', () => {
    component.filterService('OTHER');
    http.expectOne(r => r.url === '/api/v1/admin/budgets' && r.params.get('serviceCode') === 'OTHER').flush({ content: [], page: 0, totalPages: 2, totalElements: 30 });
    http.expectOne(r => r.url === '/api/v1/admin/budgets/summary' && r.params.get('serviceCode') === 'OTHER').flush({ counts: { NEW: 10, COMPLETED: 20 }, total: 30, services: [{ code: 'OTHER', name: 'Outro', total: 30 }] });
    expect(component.serviceCounts[0].total).toBe(30);
    expect(component.percent(20)).toBe(67);
    expect(component.serviceWidth(30)).toBe(100);
  });
  it('handles an empty dashboard without invalid chart values', () => {
    expect(component.percent(0)).toBe(0);
    expect(component.serviceWidth(0)).toBe(0);
    expect(component.statusGradient).not.toContain('NaN');
  });
  it('requires a reason for reopening a closed request', () => {
    component.edit.controls.status.setValue('IN_PROGRESS'); component.changeStatus();
    expect(component.detailError).toContain('motivo');
    http.expectNone('/api/v1/admin/budgets/budget-1/status');
  });
  it('reloads details after a version conflict and preserves the draft', () => {
    component.edit.patchValue({ status: 'IN_PROGRESS', justification: 'Novo atendimento' }); component.changeStatus();
    const request = http.expectOne('/api/v1/admin/budgets/budget-1/status');
    expect(request.request.body.version).toBe(3);
    request.flush({}, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/admin/budgets/budget-1').flush(details);
    http.expectOne(r => r.url === '/api/v1/admin/budgets').flush({ content: [], page: 0, totalPages: 0, totalElements: 0 });
    http.expectOne(r => r.url === '/api/v1/admin/budgets/summary').flush({ counts: {}, total: 0 });
    expect(component.detailError).toContain('alterado');
    expect(component.edit.controls.justification.value).toBe('Novo atendimento');
  });
  it('does not submit changes with a demo account', () => {
    TestBed.inject(AuthService).user.set({ id: 'demo', email: 'demo@example.test', role: 'DEMO' });
    component.edit.patchValue({ status: 'IN_PROGRESS', justification: 'Teste', note: 'Teste' });
    component.changeStatus(); component.addNote(); http.expectNone(r => r.method === 'POST' || r.method === 'PATCH');
    expect(component.saving).toBeFalse();
  });
});
