import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { BudgetFormComponent, phoneValidator } from './budget-form.component';
import { COMPANY } from '../core/company';

describe('BudgetFormComponent', () => {
  let fixture: ComponentFixture<BudgetFormComponent>;
  let component: BudgetFormComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BudgetFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    fixture = TestBed.createComponent(BudgetFormComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  function fillForm(): void {
    component.budgetForm.setValue({
      nome: ' Cliente de teste ',
      telefone: '(33) 99999-9999',
      servico: 'Assistência técnica',
      mensagem: ' Manutenção de notebook '
    });
  }

  it('mostra erros acessíveis e não envia campos obrigatórios vazios', () => {
    component.submitBudget();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[aria-invalid="true"]').length).toBe(3);
    expect(fixture.nativeElement.querySelector('#nome-error').textContent).toContain('Informe seu nome');
    expect(fixture.nativeElement.querySelector('#telefone').getAttribute('aria-describedby')).toContain('telefone-error');
    http.expectNone(COMPANY.formEndpoint);
  });

  it('rejeita nome com apenas espaços e telefone incompleto', () => {
    fillForm();
    component.budgetForm.patchValue({ nome: '   ', telefone: '9999' });
    component.submitBudget();
    expect(component.budgetForm.invalid).toBeTrue();
    http.expectNone(COMPANY.formEndpoint);
  });

  it('aceita telefone fixo, celular e código do Brasil, mas rejeita letras', () => {
    ['(33) 3412-2826', '(33) 99999-9999', '+55 (33) 99999-9999'].forEach(value => {
      expect(phoneValidator(new FormControl(value))).toBeNull();
    });
    ['', '123', 'abc33999999999', '00999999999'].forEach(value => {
      expect(phoneValidator(new FormControl(value))).not.toBeNull();
    });
  });

  it('envia os campos originais ao Formspree e impede envio duplicado', () => {
    fillForm();
    component.submitBudget();
    component.submitBudget();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button').disabled).toBeTrue();
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBeTrue();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Enviando');
    const request = http.expectOne(COMPANY.formEndpoint);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Accept')).toBe('application/json');
    expect(request.request.body.get('nome')).toBe('Cliente de teste');
    expect(request.request.body.get('telefone')).toBe('(33) 99999-9999');
    expect(request.request.body.get('servico')).toBe('Assistência técnica');
    expect(request.request.body.get('mensagem')).toBe('Manutenção de notebook');
    expect(request.request.body.get('_subject')).toBe('Novo Orçamento - Telemicro Informática');
    request.flush({ ok: true });
    fixture.detectChanges();
    expect(component.submitting).toBeFalse();
    expect(component.success).toBeTrue();
    expect(component.budgetForm.controls.nome.value).toBe('');
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Pedido enviado');
    expect(fixture.nativeElement.querySelectorAll('[aria-invalid="true"]').length).toBe(0);
  });

  it('preserva os dados na falha e permite uma nova tentativa', () => {
    fillForm();
    component.submitBudget();
    http.expectOne(COMPANY.formEndpoint).flush({}, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();
    expect(component.submitting).toBeFalse();
    expect(component.success).toBeFalse();
    expect(component.budgetForm.controls.nome.value).toBe(' Cliente de teste ');
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Seus dados foram mantidos');
    expect(fixture.nativeElement.querySelector('[role="alert"] a').href).toBe(COMPANY.whatsapp);
    component.submitBudget();
    expect(component.errorMessage).toBe('');
    http.expectOne(COMPANY.formEndpoint).flush({ ok: true });
    expect(component.success).toBeTrue();
  });

  it('permite tentar novamente após falha de conexão', () => {
    fillForm();
    component.submitBudget();
    http.expectOne(COMPANY.formEndpoint).error(new ProgressEvent('error'));
    expect(component.submitting).toBeFalse();
    expect(component.errorMessage).toContain('Não foi possível confirmar');
    expect(component.budgetForm.valid).toBeTrue();
  });

  it('encerra a espera após 20 segundos e mantém os dados', fakeAsync(() => {
    fillForm();
    component.submitBudget();
    const request = http.expectOne(COMPANY.formEndpoint);
    tick(20001);
    expect(request.cancelled).toBeTrue();
    expect(component.submitting).toBeFalse();
    expect(component.errorMessage).toContain('Não foi possível confirmar');
    expect(component.budgetForm.valid).toBeTrue();
  }));
});
