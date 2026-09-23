import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Telemicro Informática',
    loadComponent: () => import('./pages/home/home.component').then((module) => module.HomeComponent)
  },

  { path: '**', redirectTo: '' }
];
