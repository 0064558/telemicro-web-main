import { Component, DestroyRef, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators, ValidationErrors } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { apiError } from '../../../core/api';

// A função passwordsMatch é um validador personalizado que verifica se os valores dos campos newPassword e confirmPassword são iguais.
function passwordsMatch(
  control: AbstractControl
): ValidationErrors | null {
  const newPassword = control.get('newPassword')?.value;
  const confirmation = control.get('confirmPassword')?.value;

  // Se os valores forem iguais, retorna null (sem erros). Caso contrário, retorna um objeto de erro indicando que as senhas não correspondem.
  return newPassword === confirmation
    ? null
    : { passwordMismatch: true };
}

@Component({
  selector: 'app-change-password',
  imports: [
    ReactiveFormsModule,
  ],
  standalone: true,
  template: `
    <section class="detail">
      <h2>Alterar senha</h2>
    <p>Informe sua senha atual e escolha uma nova senha.</p>

    <form [formGroup]="form"
      (ngSubmit)="submit()"
      [attr.aria-busy]="saving"
      >
      <fieldset [disabled]="saving">
        <legend>Dados para alteração</legend>

        <div class="password-field">
          <label for="change-current-password">Senha atual</label>
          <div class="password-control">
          <input
            id="change-current-password"
            [type]="showCurrentPassword ? 'text' : 'password'"
            formControlName="currentPassword"
            autocomplete="current-password"
            maxlength="72"
            required
          />
          <button type="button" class="password-toggle" aria-controls="change-current-password"
            [attr.aria-label]="showCurrentPassword ? 'Ocultar senha atual' : 'Mostrar senha atual'"
            (click)="showCurrentPassword = !showCurrentPassword">{{ showCurrentPassword ? 'Ocultar' : 'Mostrar' }}</button>
          </div>
        </div>

        <div class="password-field">
          <label for="change-new-password">Nova senha</label>
          <div class="password-control">
          <input
            id="change-new-password"
            [type]="showNewPassword ? 'text' : 'password'"
            formControlName="newPassword"
            autocomplete="new-password"
            minlength="12"
            maxlength="72"
            aria-describedby="new-password-help"
            required
          />
          <button type="button" class="password-toggle" aria-controls="change-new-password"
            [attr.aria-label]="showNewPassword ? 'Ocultar nova senha' : 'Mostrar nova senha'"
            (click)="showNewPassword = !showNewPassword">{{ showNewPassword ? 'Ocultar' : 'Mostrar' }}</button>
          </div>
        </div>

        <p id="new-password-help">
          Use entre 12 e 72 caracteres.
        </p>

        <div class="password-field">
          <label for="change-confirm-password">Confirmar nova senha</label>
          <div class="password-control">
          <input
            id="change-confirm-password"
            [type]="showConfirmPassword ? 'text' : 'password'"
            formControlName="confirmPassword"
            autocomplete="new-password"
            maxlength="72"
            required
          />
          <button type="button" class="password-toggle" aria-controls="change-confirm-password"
            [attr.aria-label]="showConfirmPassword ? 'Ocultar confirmação da senha' : 'Mostrar confirmação da senha'"
            (click)="showConfirmPassword = !showConfirmPassword">{{ showConfirmPassword ? 'Ocultar' : 'Mostrar' }}</button>
          </div>
        </div>
      </fieldset>

      @if (
        form.controls.newPassword.touched &&
        form.controls.newPassword.invalid
      ) {
        <p class="error" role="alert">
          A nova senha deve ter entre 12 e 72 caracteres.
        </p>
      }

      @if (
        form.controls.confirmPassword.touched &&
        form.hasError('passwordMismatch')
      ) {
        <p class="error" role="alert">
          A confirmação deve ser igual à nova senha.
        </p>
      }

      @if (form.touched && form.invalid) {
  <p role="alert">
    Preencha os campos obrigatórios e confira as senhas.
  </p>
}

  @if (error) {
    <p class="error" role="alert">{{ error }}</p>
  }

  @if (success) {
   <p class="notice" role="status">{{ success }}</p>
  }

  <button type="submit" class="button" [disabled]="saving">
  {{ saving ? 'Salvando…' : 'Alterar senha' }}
</button>
    </form>
    </section>
  `,
  styleUrl: '../admin.css',
})
export class ChangePassword {

  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  saving = false;
  error = '';
  success = '';
  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;

  // O FormBuilder é um serviço do Angular que facilita a criação de formulários reativos. 
  // Ele fornece métodos para criar grupos de controles de formulário, tornando o processo mais conciso e legível.
  readonly form = inject(FormBuilder).nonNullable.group({
    // O Validators é um conjunto de funções fornecidas pelo Angular que permitem validar os valores dos controles de formulário.
    // O campo currentPassword é validado para garantir que o usuário forneça uma senha atual, com no máximo 72 caracteres.
    currentPassword: [
      '',
      [Validators.required, Validators.maxLength(72)]
    ],
    // O campo newPassword é validado para garantir que o usuário forneça uma senha com pelo menos 12 caracteres e no máximo 72 caracteres.
    newPassword: [
      '',
      [
        Validators.required,
        Validators.minLength(12),
        Validators.maxLength(72)
      ]
    ],
    // O campo confirmPassword é validado para garantir que o usuário forneça uma confirmação da nova senha, com no máximo 72 caracteres.
    confirmPassword: [
      '',
      [Validators.required, Validators.maxLength(72)]
    ]
  },
    { validators: passwordsMatch }
  );

  submit(): void {
    // Se o processo de alteração de senha já estiver em andamento, a função retorna imediatamente para evitar múltiplas submissões simultâneas.
    if (this.saving) return;

    // Antes de iniciar o processo de alteração de senha, limpa as mensagens de erro e sucesso para garantir que o usuário receba feedback atualizado sobre a operação.
    this.error = '';
    this.success = '';

    // Se o formulário for inválido, marca todos os campos como tocados para exibir mensagens de erro e retorna sem prosseguir com a alteração de senha.
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    // Obtém os valores dos campos currentPassword e newPassword do formulário usando o método getRawValue(), que retorna um objeto contendo os valores atuais dos controles de formulário.
    const { currentPassword, newPassword } = this.form.getRawValue();

    // Define a propriedade saving como true para indicar que o processo de alteração de senha está em andamento, desabilitando a interface do usuário para evitar múltiplas submissões.
    this.saving = true;

    // Chama o método changePassword do serviço AuthService, passando a senha atual e a nova senha como argumentos. O método retorna um Observable que representa a operação assíncrona de alteração de senha.
    this.auth.changePassword(currentPassword, newPassword)
      // O operador pipe é usado para encadear operadores de transformação e manipulação de fluxo de dados em observáveis. 
      // Aqui, ele é usado para aplicar o operador takeUntilDestroyed, que cancela a assinatura do Observable quando o componente é destruído, evitando vazamentos de memória. 
      // Além disso, o operador finalize é usado para executar uma ação final (definir saving como false) independentemente do resultado da operação (sucesso ou erro).
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving = false)
      )
      // O método subscribe é chamado para iniciar a execução do Observable e lidar com os resultados da operação de alteração de senha. 
      // Ele recebe um objeto com callbacks para os casos de sucesso (next) e erro (error).
      .subscribe({
        // O callback next é chamado quando a operação de alteração de senha é bem-sucedida. Ele redefine o formulário, limpando os campos, e define a mensagem de sucesso para informar ao usuário que a senha foi alterada com sucesso.
        next: () => {
          this.form.reset();
          this.showCurrentPassword = false;
          this.showNewPassword = false;
          this.showConfirmPassword = false;
          this.success = 'Senha alterada com sucesso.';
        },
        // O callback error é chamado quando ocorre um erro durante a operação de alteração de senha. Ele verifica o status do erro e define mensagens de erro apropriadas para informar ao usuário sobre o problema.
        error: error => {
          if (error.status === 0 || error.name === 'TimeoutError') {
            this.error =
              'Não foi possível confirmar a alteração. Tente entrar com a nova senha para verificar se ela foi salva.';
          } else if (error.status === 409) {
            this.error =
              'Os dados da conta mudaram durante a alteração. Tente novamente.';
          } else {
            this.error = apiError(error);
          }
        }
      });
  }
}
