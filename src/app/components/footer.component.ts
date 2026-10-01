import { Component } from '@angular/core';
import { COMPANY } from '../core/company';

@Component({
  selector: 'app-footer',
  standalone: true,
  template: `
    <footer class="site-footer">
      <div class="container footer-top">
        <div><a href="#inicio"><img src="assets/img/LogoModificada2_resized.png" width="115" height="85" alt="Telemicro Informática — início" loading="lazy" /></a><p>Assistência técnica, suprimentos e acessórios de informática.<br />{{ company.city }}</p></div>
        <nav aria-label="Navegação do rodapé"><a href="#servicos">Nossos serviços</a><a href="#galeria">Conheça a loja</a><a href="#contato">Pedir um orçamento</a></nav>
        <div class="footer-contact"><a [href]="company.whatsapp" target="_blank" rel="noopener noreferrer">{{ company.phone }}</a><a [href]="'mailto:' + company.email">{{ company.email }}</a><a [href]="company.maps" target="_blank" rel="noopener noreferrer">{{ company.street }}</a></div>
      </div>
      <div class="container footer-bottom"><span>© {{ currentYear }} Telemicro Informática. Todos os direitos reservados.</span><a href="#inicio">Voltar ao início ↑</a></div>
    </footer>
  `,
  styles: [`
    .site-footer { position: relative; background: rgba(3,20,39,.97); color: var(--on-dark-muted); padding: 48px 0 24px; }
    .site-footer::before { content: ''; position: absolute; inset: 0 0 auto; height: 1px; background: linear-gradient(90deg, transparent, var(--brand-red), var(--brand-blue), var(--cyan), transparent); opacity: .65; }
    .footer-top { display: grid; grid-template-columns: 1.4fr 1fr 1.2fr; gap: 40px; padding-bottom: 40px; }
    img { width: 115px; height: 85px; object-fit: contain; margin-bottom: 12px; }
    p { font-size: 13px; }
    nav, .footer-contact { display: flex; flex-direction: column; align-items: flex-start; gap: 16px; padding-top: 24px; }
    a { color: inherit; font-size: 13px; text-decoration: none; overflow-wrap: anywhere; }
    a:hover { color: var(--cyan); }
    .footer-bottom { display: flex; justify-content: space-between; gap: 24px; border-top: 1px solid var(--line-dark); padding-top: 24px; font-size: 12px; }
    @media(max-width: 700px) { .footer-top { grid-template-columns: 1fr; gap: 16px; } .footer-bottom { flex-direction: column; } }
  `]
})
export class FooterComponent {
  readonly company = COMPANY;
  readonly currentYear = new Date().getFullYear();
}
