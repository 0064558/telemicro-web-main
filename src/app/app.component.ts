import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FooterComponent } from './components/footer.component';
import { HeaderComponent } from './components/header.component';
import { ParticleBackgroundComponent } from './components/particle-background.component';
import { FloatingWhatsappComponent } from './components/floating-whatsapp.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, FooterComponent, ParticleBackgroundComponent, FloatingWhatsappComponent],
  template: `
    <a class="skip-link" href="#conteudo">Pular para o conteúdo</a>
    <app-particle-background />
    <div class="site-shell">
      <app-header />
      <main id="conteudo" tabindex="-1"><router-outlet /></main>
      <app-footer />
      <app-floating-whatsapp />
    </div>
  `
})
export class AppComponent {}
