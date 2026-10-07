import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { problemDetailInterceptor } from './core/problem-detail.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding: route parameters such as :id arrive as component inputs
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([problemDetailInterceptor])),
  ],
};
