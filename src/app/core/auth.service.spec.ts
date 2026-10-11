import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { AuthService, authInterceptor } from './auth.service';

describe('Admin authentication', () => {
  let auth: AuthService; let http: HttpTestingController; let client: HttpClient;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService); http = TestBed.inject(HttpTestingController); client = TestBed.inject(HttpClient);
    spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
  });
  afterEach(() => { auth.logout(); http.verify(); });
  function login() {
    auth.login('admin@example.test', 'password').subscribe();
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 'test-token', expiresIn: 60, user: { id: '1', email: 'admin@example.test', role: 'ADMIN' } });
  }
  it('sends the token only to protected API endpoints', () => {
    login();
    client.get('/api/v1/admin/budgets').subscribe();
    const admin = http.expectOne('/api/v1/admin/budgets');
    expect(admin.request.headers.get('Authorization')).toBe('Bearer test-token'); admin.flush({});
    for (const url of ['/api/v1/services', 'https://example.test/admin/budgets']) {
      client.get(url).subscribe(); const req = http.expectOne(url);
      expect(req.request.headers.has('Authorization')).toBeFalse(); req.flush({});
    }
  });
  it('clears credentials when the server rejects the session', () => {
    login(); client.get('/api/v1/admin/budgets').subscribe({ error: () => {} });
    http.expectOne('/api/v1/admin/budgets').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.accessToken).toBe(''); expect(auth.user()).toBeNull(); expect(auth.expired()).toBeTrue();
  });
  it('expires the session without persisting the token', fakeAsync(() => {
    login(); tick(60000); expect(auth.accessToken).toBe(''); expect(auth.expired()).toBeTrue();
  }));
});

describe('Published API configuration', () => {
  it('uses the configured HTTPS API and keeps the token away from other origins', () => {
    const previousConfig = window.telemicroConfig;
    window.telemicroConfig = { apiUrl: 'https://api.example.test/api/v1' };
    let auth: AuthService | undefined;
    try {
      TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
      auth = TestBed.inject(AuthService);
      const http = TestBed.inject(HttpTestingController);
      const client = TestBed.inject(HttpClient);
      spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
      auth.login('admin@example.test', 'password').subscribe();
      http.expectOne('https://api.example.test/api/v1/auth/login').flush({
        accessToken: 'test-token', expiresIn: 60,
        user: { id: '1', email: 'admin@example.test', role: 'ADMIN' }
      });
      client.get('https://api.example.test/api/v1/admin/budgets').subscribe();
      const admin = http.expectOne('https://api.example.test/api/v1/admin/budgets');
      expect(admin.request.headers.get('Authorization')).toBe('Bearer test-token');
      admin.flush({});
      client.get('https://other.example.test/api/v1/admin/budgets').subscribe();
      const other = http.expectOne('https://other.example.test/api/v1/admin/budgets');
      expect(other.request.headers.has('Authorization')).toBeFalse();
      other.flush({});
      http.verify();
    } finally {
      auth?.logout();
      window.telemicroConfig = previousConfig;
    }
  });
});
