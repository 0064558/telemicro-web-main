import { Component, ElementRef, HostListener, ViewChild } from '@angular/core';
import { COMPANY } from '../core/company';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [IconComponent],
  template: `
    <header class="site-header">
      <div class="container header-inner">
        <a href="#inicio" class="brand" aria-label="Telemicro Informática — início" (click)="goToStart($event)">
          <img src="assets/img/LogoModificada2_resized.png" width="500" height="370" alt="Telemicro Informática" />
        </a>
        <button #menuButton class="menu-toggle" type="button" aria-controls="principal" [attr.aria-expanded]="isMenuOpen" (click)="isMenuOpen = !isMenuOpen">
          {{ isMenuOpen ? 'Fechar' : 'Menu' }} <span aria-hidden="true">{{ isMenuOpen ? '×' : '☰' }}</span>
        </button>
        <nav id="principal" class="main-nav" [class.is-open]="isMenuOpen" aria-label="Navegação principal" (click)="closeAfterNavigation($event)">
          <a href="#servicos">Serviços</a>
          <a href="#galeria">A Telemicro</a>
          <a href="#contato">Contato</a>
          <a class="button button-small" [href]="company.whatsapp" target="_blank" rel="noopener noreferrer">Fale com a gente <app-icon name="diagonal" /></a>
        </nav>
      </div>
    </header>
  `,
  styleUrl: './header.component.css'
})
export class HeaderComponent {
  readonly company = COMPANY;
  isMenuOpen = false;
  @ViewChild('menuButton') menuButton?: ElementRef<HTMLButtonElement>;

  goToStart(event: MouseEvent): void {
    if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;

    event.preventDefault();
    this.isMenuOpen = false;
    history.replaceState(null, '', '#inicio');
    window.scrollTo({
      top: 0,
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
    });
  }

  closeAfterNavigation(event: MouseEvent): void {
    const link = (event.target as HTMLElement).closest('a');
    if (!link) return;
    const wasOpen = this.isMenuOpen;
    this.isMenuOpen = false;
    if (wasOpen) {
      const href = link.getAttribute('href');
      if (href?.startsWith('#')) {
        document.getElementById(href.slice(1))?.focus({ preventScroll: true });
      } else {
        this.menuButton?.nativeElement.focus();
      }
    }
  }

  @HostListener('document:keydown.escape') closeMenu(): void {
    if (this.isMenuOpen) {
      this.isMenuOpen = false;
      this.menuButton?.nativeElement.focus();
    }
  }
}
