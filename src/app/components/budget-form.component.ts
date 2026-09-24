import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize, timeout } from 'rxjs';
import { COMPANY } from '../core/company';
import { IconComponent } from './icon.component';

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
  readonly options = ['Assistência técnica', 'Equipamentos e acessórios', 'Recarga de cartuchos e toner', 'Suporte presencial ou online', 'Locação de equipamentos', 'Manutenção preventiva', 'Outro assunto'];
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

    const formData = new FormData();
    Object.entries(this.budgetForm.getRawValue()).forEach(([key, value]) => formData.append(key, value.trim()));
    formData.append('_subject', 'Novo Orçamento - Telemicro Informática');
    this.submitting = true;
    // Enviar o formulário para o endpoint da empresa usando HttpClient
    this.http.post(COMPANY.formEndpoint, formData, { headers: { Accept: 'application/json' } })
      .pipe(timeout(20000), takeUntilDestroyed(this.destroyRef), finalize(() => (this.submitting = false)))
      .subscribe({
        // Se a requisição for bem-sucedida, exibir uma mensagem de sucesso e limpar o formulário
        next: () => {
          this.success = true;
          this.budgetForm.reset();
        },
        error: () => {
          this.errorMessage = 'Não foi possível confirmar o envio. Seus dados foram mantidos. Tente novamente ou fale com a gente pelo WhatsApp.';
        }
      });
  }
}
