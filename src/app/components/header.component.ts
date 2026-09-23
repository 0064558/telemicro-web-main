import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <header>
      <div class="nav-container">
        <a routerLink="/" class="logo" aria-label="Telemicro Informática - início">
          <img src="assets/img/LogoModificada2.png" alt="Telemicro Informática" />
        </a>

        <nav aria-label="Navegação principal">
          <ul>
            <li><a routerLink="/" fragment="inicio">Início</a></li>
            <li><a routerLink="/" fragment="servicos">Serviços</a></li>
            <li><a routerLink="/" fragment="galeria">Loja</a></li>
            <li><a routerLink="/" fragment="contato">Contato</a></li>
          </ul>
        </nav>

        <button id="menu-toggle" class="menu-toggle" type="button" aria-label="Abrir/fechar menu" [attr.aria-expanded]="isMenuOpen" (click)="toggleMenu()">
          <span></span><span></span><span></span>
        </button>

        <nav id="mobile-menu" class="mobile-menu" [class.active]="isMenuOpen" aria-label="Navegação mobile">
          <ul>
            <li><a routerLink="/" fragment="inicio" (click)="closeMenu()">Início</a></li>
            <li><a routerLink="/" fragment="servicos" (click)="closeMenu()">Serviços</a></li>
            <li><a routerLink="/" fragment="galeria" (click)="closeMenu()">Loja</a></li>
            <li><a routerLink="/" fragment="contato" (click)="closeMenu()">Contato</a></li>
            <li><a href="https://wa.me/553334122826" target="_blank" rel="noopener noreferrer" (click)="closeMenu()">WhatsApp</a></li>
          </ul>
        </nav>

        <a href="https://wa.me/553334122826" target="_blank" rel="noopener noreferrer" class="btn-cta desktop-only">WHATSAPP</a>
      </div>
    </header>
  `
})
export class HeaderComponent {
  isMenuOpen = false;

  toggleMenu(): void {
    this.isMenuOpen = !this.isMenuOpen;
  }

  closeMenu(): void {
    this.isMenuOpen = false;
  }
}
