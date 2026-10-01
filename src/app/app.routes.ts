import { Routes } from '@angular/router';
import { adminGuard } from './core/auth.service';

export const routes: Routes = [
  { path: 'admin/login', title: 'Entrar | Telemicro', loadComponent: () => import('./pages/admin/login.component').then(m => m.LoginComponent) },
  { path: 'admin', canActivate: [adminGuard], title: 'Orçamentos | Telemicro', loadComponent: () => import('./pages/admin/admin.component').then(m => m.AdminComponent) },
  {
    path: '',
    title: 'Telemicro Informática | Assistência e equipamentos em São João Evangelista',
    loadComponent: () => import('./pages/home/home.component').then((module) => module.HomeComponent)
  },

  { path: '**', redirectTo: '' }
];
