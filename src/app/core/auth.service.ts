import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, tap, throwError, timeout } from 'rxjs';
import { API_URL } from './api';

interface User { id: string; email: string; role: 'ADMIN' | 'DEMO' }
interface Login { accessToken: string; expiresIn: number; user: User }
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);
  private readonly router = inject(Router);
  private token = '';
  private expiry = 0;
  private timer?: ReturnType<typeof setTimeout>;
  readonly user = signal<User | null>(null);
  readonly expired = signal(false);
  get accessToken(): string {
    if (this.token && Date.now() >= this.expiry) this.logout(true);
    return this.token;
  }
  login(email: string, password: string) {
    return this.http.post<Login>(`${this.api}/auth/login`, { email: email.trim(), password }).pipe(timeout(90000), tap(result => {
      clearTimeout(this.timer);
      this.token = result.accessToken;
      this.expiry = Date.now() + result.expiresIn * 1000;
      this.user.set(result.user);
      this.expired.set(false);
      this.timer = setTimeout(() => this.logout(true), result.expiresIn * 1000);
    }));
  }
  logout(expired = false): void {
    clearTimeout(this.timer);
    this.token = '';
    this.user.set(null);
    this.expired.set(expired);
    void this.router.navigateByUrl('/admin/login');
  }
}
export const adminGuard: CanActivateFn = () => inject(AuthService).accessToken ? true : inject(Router).createUrlTree(['/admin/login']);
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const api = inject(API_URL);
  const protectedRequest = request.url.startsWith(`${api}/admin/`) || request.url === `${api}/auth/me`;
  const token = protectedRequest ? auth.accessToken : '';
  return next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request).pipe(catchError(error => {
    if (protectedRequest && error.status === 401) auth.logout(true);
    return throwError(() => error);
  }));
};
