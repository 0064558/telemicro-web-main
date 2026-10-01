import { Component, DestroyRef, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { apiError } from '../../core/api';
@Component({
  standalone: true, imports: [ReactiveFormsModule, RouterLink], styleUrl: './admin.css',
  template: `<section class="admin-shell login"><a routerLink="/">← Voltar ao site</a><h1>Painel administrativo</h1><p>Entre para acompanhar os pedidos de orçamento.</p>
    @if (auth.expired()) { <p role="status">Sua sessão expirou. Entre novamente.</p> }
    <form [formGroup]="form" (ngSubmit)="submit()" [attr.aria-busy]="busy"><fieldset [disabled]="busy"><legend>Acesso à conta</legend>
    <label>E-mail<input type="email" formControlName="email" autocomplete="username" maxlength="254" required /></label>
    <label>Senha<input type="password" formControlName="password" autocomplete="current-password" maxlength="72" required /></label></fieldset>
    @if (form.invalid && form.touched) { <p role="alert">Informe um e-mail válido e sua senha.</p> }
    @if (error) { <p class="error" role="alert">{{ error }}</p> }
    <button class="button" [disabled]="busy">{{ busy ? 'Entrando…' : 'Entrar' }}</button>
    @if (busy) { <p role="status">A primeira conexão pode demorar até um minuto.</p> }</form></section>`
})
export class LoginComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  readonly form = inject(FormBuilder).nonNullable.group({ email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]], password: ['', [Validators.required, Validators.maxLength(72)]] });
  busy = false; error = '';
  submit(): void {
    if (this.busy) return;
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.busy = true; this.error = '';
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).pipe(takeUntilDestroyed(this.destroy), finalize(() => this.busy = false)).subscribe({
      next: () => { this.form.controls.password.reset(); void this.router.navigateByUrl('/admin'); },
      error: e => this.error = e.status === 401 ? 'E-mail ou senha inválidos.' : apiError(e)
    });
  }
}
