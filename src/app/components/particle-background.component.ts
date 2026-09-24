import { AfterViewInit, Component, OnDestroy } from '@angular/core';

interface ParticleInstance {
  pJS?: { fn?: { vendors?: { destroypJS?: () => void } } };
}

type ParticlesFactory = (elementId: string, configuration: object) => void;

@Component({
  selector: 'app-particle-background',
  standalone: true,
  template: `<div id="particles" aria-hidden="true"></div>`,
  styles: [`
    :host {
      position: fixed;
      z-index: 0;
      inset: 0;
      pointer-events: none;
      overflow: hidden;
      background:
        radial-gradient(circle at 78% 12%, rgba(18, 105, 190, .38), transparent 34%),
        radial-gradient(circle at 10% 68%, rgba(0, 111, 184, .2), transparent 31%),
        #041b33;
    }
    #particles { width: 100%; height: 100%; }
    :host ::ng-deep .particles-js-canvas-el { display: block; width: 100% !important; height: 100% !important; }
  `]
})
export class ParticleBackgroundComponent implements AfterViewInit, OnDestroy {
  ngAfterViewInit(): void {
    const browserWindow = window as Window & {
      particlesJS?: ParticlesFactory;
      pJSDom?: ParticleInstance[];
    };
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    browserWindow.particlesJS?.('particles', {
      particles: {
        number: { value: reduceMotion ? 35 : 80 },
        color: { value: '#00D4FF' },
        shape: { type: 'circle' },
        opacity: { value: 0.5, random: true },
        size: { value: 3, random: true },
        line_linked: {
          enable: true,
          distance: 150,
          color: '#00D4FF',
          opacity: 0.2,
          width: 1
        },
        move: { enable: !reduceMotion, speed: 2 }
      },
      interactivity: {
        events: {
          onhover: { enable: !reduceMotion, mode: 'repulse' }
        }
      },
      retina_detect: true
    });
  }

  ngOnDestroy(): void {
    const browserWindow = window as Window & { pJSDom?: ParticleInstance[] };
    browserWindow.pJSDom?.[0]?.pJS?.fn?.vendors?.destroypJS?.();
  }
}
