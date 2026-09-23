import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink],
  template: `
    <footer>
      <div class="footer-grid">
        <div>
          <img src="assets/img/LogoModificada2_resized.png" class="footer-logo" alt="Telemicro" />
          <p class="footer-about">Referência em tecnologia em São João Evangelista. Atendimento personalizado, preço justo e soluções que funcionam.</p>
          <div class="social-links"></div>
        </div>

        <div class="footer-links">
          <h4>PRODUTOS</h4>
          <a routerLink="/" fragment="galeria">Notebooks</a>
          <a routerLink="/" fragment="galeria">Computadores Gamer</a>
          <a routerLink="/" fragment="galeria">Impressoras</a>
          <a routerLink="/" fragment="galeria">Acessórios</a>
        </div>

        <div class="footer-links">
          <h4>SERVIÇOS</h4>
          <a routerLink="/" fragment="servicos">Manutenção</a>
          <a routerLink="/" fragment="servicos">Recarga de Toner</a>
          <a routerLink="/" fragment="servicos">Formatação</a>
          <a routerLink="/" fragment="servicos">Suporte</a>
        </div>

        <div class="footer-links">
          <h4>CONTATO</h4>
          <a href="https://wa.me/553334122826" target="_blank" rel="noopener noreferrer">(33) 3412-2826</a>
          <a href="mailto:rmatelemicroltda@hotmail.com">rmatelemicroltda@hotmail.com</a>
          <a routerLink="/" fragment="contato">Rua Benedito Valadares, 78</a>
          <a routerLink="/" fragment="contato">São João Evangelista - MG</a>
        </div>
      </div>

      <div class="copyright">© {{ currentYear }} <span>TELEMICRO INFORMÁTICA</span> • Todos os direitos reservados</div>
    </footer>
  `
})
export class FooterComponent {
  readonly currentYear = new Date().getFullYear();
}
