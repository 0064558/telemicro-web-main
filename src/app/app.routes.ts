import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Telemicro Informática | Assistência e equipamentos em São João Evangelista',
    loadComponent: () => import('./pages/home/home.component').then((module) => module.HomeComponent)
  },

  { path: '**', redirectTo: '' }
];
