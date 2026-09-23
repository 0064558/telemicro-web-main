import { AfterViewInit, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FooterComponent } from './components/footer.component';
import { HeaderComponent } from './components/header.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, FooterComponent],
  template: `
    <div id="particles" aria-hidden="true"></div>
    <app-header />
    <main>
      <router-outlet />
    </main>
    <app-footer />
  `
})
export class AppComponent implements AfterViewInit {
  ngAfterViewInit(): void {
    const browserWindow = window as Window & {
      AOS?: { init: (options: { duration: number; once: boolean }) => void };
      particlesJS?: (id: string, config: object) => void;
    };

    browserWindow.AOS?.init({ duration: 1200, once: true });
    browserWindow.particlesJS?.('particles', {
      particles: {
        number: { value: 80 },
        color: { value: '#00D4FF' },
        shape: { type: 'circle' },
        opacity: { value: 0.5, random: true },
        size: { value: 3, random: true },
        line_linked: { enable: true, distance: 150, color: '#00D4FF', opacity: 0.2, width: 1 },
        move: { enable: true, speed: 2 }
      },
      interactivity: { events: { onhover: { enable: true, mode: 'repulse' } } },
      retina_detect: true
    });
  }
}
