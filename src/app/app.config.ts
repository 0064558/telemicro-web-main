import { ApplicationConfig } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './core/auth.service';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';

// Esse arquivo é responsável por fornecer a configuração do aplicativo Angular, incluindo provedores de serviços e interceptadores HTTP. Ele importa os módulos necessários do Angular, define os interceptadores e configura o roteamento com suporte a rolagem em memória.
export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(
      routes,
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' })
    )
  ]
};
