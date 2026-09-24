import { AfterViewInit, Directive, ElementRef, Input, OnDestroy, Renderer2 } from '@angular/core';

@Directive({
  selector: '[appReveal]',
  standalone: true
})
export class RevealDirective implements AfterViewInit, OnDestroy {
  @Input() appRevealDelay = 0;
  private observer?: IntersectionObserver;

  constructor(
    private readonly element: ElementRef<HTMLElement>,
    private readonly renderer: Renderer2
  ) {}

  ngAfterViewInit(): void {
    const node = this.element.nativeElement;
    this.renderer.addClass(node, 'reveal');
    this.renderer.setStyle(node, '--reveal-delay', `${this.appRevealDelay}ms`);

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || !('IntersectionObserver' in window)) {
      this.renderer.addClass(node, 'is-visible');
      return;
    }

    this.observer = new IntersectionObserver(([entry]) => {
      if (!entry?.isIntersecting) return;
      this.renderer.addClass(node, 'is-visible');
      this.observer?.disconnect();
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });

    this.observer.observe(node);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}
