import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

interface ServiceCard {
  title: string;
  description: string;
}

interface GallerySlide {
  image: string;
  alt: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="hero" id="inicio">
      <div class="hero-content">
        <div class="hero-text" data-aos="fade-right">
          <h1><span class="title-top">TELEMICRO</span><span class="title-bottom">INFORMÁTICA</span></h1>
          <p>A tecnologia que São João Evangelista merece! Assistência técnica, suprimentos e acessórios de informática.</p>
          <a href="#contato" class="btn-cta">FAÇA SEU ORÇAMENTO!</a>
        </div>
        <div class="hero-image" data-aos="fade-left">
          <img src="assets/img/loja.jpg" alt="Interior da loja Telemicro Informática" width="1600" height="900" decoding="async" fetchpriority="high" />
        </div>
      </div>
    </section>

    <section class="section" id="servicos">
      <div class="container">
        <h2 class="section-title" data-aos="fade-up">NOSSOS SERVIÇOS</h2>
        <div class="services-grid">
          <div class="service-card" *ngFor="let service of services; let index = index" data-aos="fade-up" [attr.data-aos-delay]="(index + 1) * 100">
            <h3>{{ service.title }}</h3>
            <p>{{ service.description }}</p>
          </div>
        </div>
      </div>
    </section>

    <section class="section" id="galeria">
      <div class="container">
        <h2 class="section-title" data-aos="fade-up">NOSSA LOJA</h2>
        <div class="carousel" data-aos="fade-up">
          <button class="carousel-btn prev" type="button" aria-label="Imagem anterior" (click)="moveCarousel(-1)">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" /></svg>
          </button>
          <button class="carousel-btn next" type="button" aria-label="Próxima imagem" (click)="moveCarousel(1)">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" /></svg>
          </button>
          <div class="carousel-track" [style.transform]="'translateX(-' + currentSlide * 100 + '%)'">
            <div class="carousel-slide" *ngFor="let slide of gallery; let index = index">
              <img [src]="slide.image" [alt]="slide.alt" width="1200" height="675" loading="lazy" [attr.aria-hidden]="index !== currentSlide" />
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="contact section" id="contato">
      <div class="container">
        <h2 class="section-title" data-aos="fade-up">FAÇA SEU ORÇAMENTO!</h2>
        <div class="contact-grid">
          <div class="form-container" data-aos="fade-right">
            <form [formGroup]="budgetForm" (ngSubmit)="submitBudget()" novalidate>
              <label for="nome" class="sr-only">Nome</label>
              <input id="nome" type="text" formControlName="nome" required placeholder="Seu nome" autocomplete="name" />
              <label for="telefone" class="sr-only">Telefone</label>
              <input id="telefone" type="tel" formControlName="telefone" required placeholder="(33) 3412-2826" autocomplete="tel" />
              <label for="servico" class="sr-only">Serviço desejado</label>
              <select id="servico" formControlName="servico" required>
                <option value="">Selecione o serviço...</option>
                <option>Formatação</option>
                <option>Recarga de Toner</option>
                <option>Compra de Computador</option>
                <option>Manutenção</option>
                <option>Outro</option>
              </select>
              <label for="mensagem" class="sr-only">Mensagem</label>
              <textarea id="mensagem" formControlName="mensagem" rows="5" placeholder="Descreva seu problema..."></textarea>
              <button type="submit" class="btn-submit" [disabled]="submitting">
                {{ submitting ? 'ENVIANDO...' : 'ENVIAR POR EMAIL' }}
              </button>
            </form>
            <div class="success-message" [class.show]="success" role="status" aria-live="polite" [attr.aria-hidden]="!success">
              Enviado com sucesso!<br />Aguarde que logo responderemos!
            </div>
            <p class="form-error" *ngIf="errorMessage" role="alert">{{ errorMessage }}</p>
          </div>

          <div class="map-container" data-aos="fade-left">
            <iframe title="Localização da Telemicro Informática no mapa" src="https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d3782.456789012345!2d-42.891234685!3d-18.567890187!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0xa5b8c8d8e8f9a0b%3A0x123456789abcde!2sR.%20Benedito%20Valadares%2C%2078%20-%20Centro%2C%20S%C3%A3o%20Jo%C3%A3o%20Evangelista%20-%20MG!5e0!3m2!1spt-BR!2sbr!4v1735680000000" width="100%" height="685" loading="lazy" allowfullscreen></iframe>
          </div>
        </div>
      </div>
    </section>
  `
})
export class HomeComponent implements OnDestroy {
  readonly services: ServiceCard[] = [
    { title: 'MANUTENÇÃO ESPECIALIZADA', description: 'Formatação, Upgrade, Limpeza e Recuperação.' },
    { title: 'VENDA DE EQUIPAMENTOS DE INFORMÁTICA', description: 'Venda de PCs, Notebooks, Monitores, Teclados, Mouses, Cabos e muito mais.' },
    { title: 'RECARGA DE CARTUCHO E TONER', description: 'Qualidade Original por até 70% menos.' },
    { title: 'SUPORTE PRESENCIAL E ONLINE', description: 'Atendimento Remoto ou Presencial.' },
    { title: 'LOCAÇÃO', description: 'Impressoras e Computadores.' },
    { title: 'PLANOS', description: 'Plano de Manutenção Preventiva.' }
  ];

  readonly gallery: GallerySlide[] = [
    { image: 'assets/img/manutencao-especializada.jpg', alt: 'Manutenção especializada' },
    { image: 'assets/img/computador.jpg', alt: 'Computador' }
  ];

  readonly budgetForm = this.formBuilder.nonNullable.group({
    nome: ['', Validators.required],
    telefone: ['', Validators.required],
    servico: ['', Validators.required],
    mensagem: ['']
  });

  currentSlide = 0;
  submitting = false;
  success = false;
  errorMessage = '';
  private readonly carouselTimer: number;

  constructor(private readonly formBuilder: FormBuilder, private readonly http: HttpClient) {
    this.carouselTimer = window.setInterval(() => this.moveCarousel(1), 6000);
  }

  moveCarousel(direction: number): void {
    this.currentSlide = (this.currentSlide + direction + this.gallery.length) % this.gallery.length;
  }

  submitBudget(): void {
    if (this.budgetForm.invalid) {
      this.budgetForm.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.success = false;
    this.errorMessage = '';
    const formData = new FormData();
    Object.entries(this.budgetForm.getRawValue()).forEach(([key, value]) => formData.append(key, value));
    formData.append('_subject', 'Novo Orçamento - Telemicro Informática');

    this.http.post('https://formspree.io/f/xeowyana', formData, { headers: { Accept: 'application/json' } }).pipe(finalize(() => (this.submitting = false))).subscribe({
      next: () => {
        this.success = true;
        this.budgetForm.reset();
        window.setTimeout(() => (this.success = false), 4000);
      },
      error: () => {
        this.errorMessage = 'Erro ao enviar. Verifique sua conexão ou tente novamente mais tarde.';
      },
    });
  }

  ngOnDestroy(): void {
    window.clearInterval(this.carouselTimer);
  }
}
