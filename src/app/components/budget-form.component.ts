import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize, timeout } from 'rxjs';
import { COMPANY } from '../core/company';
import { IconComponent } from './icon.component';
import { API_URL, ServiceOption, apiError } from '../core/api';

export function phoneValidator(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '').trim();
  const digits = value.replace(/\D/g, '');
  return /^[+\d\s().-]+$/.test(value) && /^(?:55)?[1-9]\d[2-9]\d{7,8}$/.test(digits)
    ? null : { phone: true };
}

@Component({
  selector: 'app-budget-form',
  standalone: true,
  imports: [ReactiveFormsModule, IconComponent],
  templateUrl: './budget-form.component.html',
  styleUrl: './budget-form.component.css'
})
export class BudgetFormComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  @ViewChild('formElement') formElement?: ElementRef<HTMLFormElement>;

  readonly company = COMPANY;
  private readonly api = inject(API_URL);
  options: ServiceOption[] = [];
  catalogLoading = true;
  catalogError = '';
  protocol = '';
  private retryPayload = '';
  private retryKey = '';
  constructor() { this.loadServices(); }
  loadServices(): void {
    if (!this.catalogLoading && this.options.length) return;
    this.catalogLoading = true;
    this.catalogError = '';
    this.http.get<ServiceOption[]>(`${this.api}/services`).pipe(timeout(90000), takeUntilDestroyed(this.destroyRef), finalize(() => this.catalogLoading = false)).subscribe({
      next: options => { this.options = options; if (!options.length) this.catalogError = 'Nenhum serviço disponível no momento.'; },
      error: () => this.catalogError = 'Não foi possível carregar os serviços. Tente novamente.'
    });
  }
  readonly budgetForm = this.formBuilder.nonNullable.group({
    nome: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    telefone: ['', [Validators.required, phoneValidator]],
    servico: ['', Validators.required],
    mensagem: ['', Validators.maxLength(2000)]
  });
  submitting = false;
  success = false;
  errorMessage = '';

  invalid(name: 'nome' | 'telefone' | 'servico' | 'mensagem'): boolean {
    const field = this.budgetForm.controls[name];
    return field.invalid && field.touched;
  }

  submitBudget(): void {
    if (this.submitting) return;
    this.success = false;
    this.errorMessage = '';
    if (this.budgetForm.invalid) {
      this.budgetForm.markAllAsTouched();
      this.formElement?.nativeElement.querySelector<HTMLElement>('.ng-invalid')?.focus();
      return;
    }

    const value = this.budgetForm.getRawValue();
    if (!this.options.some(option => option.code === value.servico)) { this.errorMessage = 'Selecione um serviço disponível.'; return; }
    const payload = { customerName: value.nome.trim(), phone: value.telefone.trim(), serviceCode: value.servico, message: value.mensagem.trim() };
    const serialized = JSON.stringify(payload);
    if (serialized !== this.retryPayload) { this.retryPayload = serialized; this.retryKey = crypto.randomUUID(); }
    this.submitting = true;
    this.http.post<{ protocol: string }>(`${this.api}/budgets`, payload, { headers: { 'Idempotency-Key': this.retryKey } })
      .pipe(timeout(90000), takeUntilDestroyed(this.destroyRef), finalize(() => (this.submitting = false)))
      .subscribe({
        // Se a requisição for bem-sucedida, exibir uma mensagem de sucesso e limpar o formulário
        next: response => {
          this.protocol = response.protocol;
          this.retryPayload = '';
          this.retryKey = '';
          this.success = true;
          this.budgetForm.reset();
        },
        error: error => {
          this.errorMessage = ((error.status === 400 || error.status === 429) ? apiError(error) : 'Não foi possível confirmar o envio.') + ' Seus dados foram mantidos. Tente novamente ou fale com a gente pelo WhatsApp.';
        }
      });
  }
}
