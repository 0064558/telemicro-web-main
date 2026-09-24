import { Component, Input } from '@angular/core';

export type IconName = 'arrow' | 'diagonal' | 'tools' | 'monitor' | 'printer' | 'support' | 'rental' | 'shield' | 'pin' | 'message' | 'mail' | 'check';

@Component({
  selector: 'app-icon',
  standalone: true,
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="paths[name]" /></svg>`,
  styles: [`:host { display: inline-flex; width: 24px; height: 24px; flex-shrink: 0; } svg { width: 100%; height: 100%; }`]
})
export class IconComponent {
  @Input() name: IconName = 'arrow';
  readonly paths: Record<IconName, string> = {
    arrow: 'M4 12h16m-6-6 6 6-6 6',
    diagonal: 'M6 18 18 6M6 6h12v12',
    tools: 'm14 6 4 4M14 3a6 6 0 0 0-7 8l-5 7 4 4 7-5a6 6 0 0 0 8-7l-5 2-4-4 2-5Z',
    monitor: 'M3 4h18v13H3ZM8 21h8m-4-4v4M7 8h5',
    printer: 'M6 9V3h12v6M6 17H3V9h18v8h-3M6 14h12v7H6Zm11-2h1',
    support: 'M4 13v-2a8 8 0 0 1 16 0v2M4 11H2v7h4v-7ZM20 11h2v7h-4v-7ZM20 18v3h-7',
    rental: 'M4 8a9 9 0 0 1 15-3l2 3M21 3v5h-5M20 16a9 9 0 0 1-15 3l-2-3M3 21v-5h5M9 12h6',
    shield: 'M12 2 3 6v6c0 5 9 10 9 10s9-5 9-10V6ZM8 12l3 3 5-6',
    pin: 'M19 10c0 5-7 12-7 12S5 15 5 10a7 7 0 1 1 14 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
    message: 'M21 11.5a9 9 0 0 1-13.5 7.8L3 21l1.7-4.5A9 9 0 1 1 21 11.5ZM8 9h8m-8 5h5',
    mail: 'M3 5h18v14H3Zm0 0 9 8 9-8',
    check: 'm5 12 4 4L19 6'
  };
}
