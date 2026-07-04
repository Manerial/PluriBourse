import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { DateAdapter, MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatIconRegistry } from '@angular/material/icon';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthService } from './services/auth.service';

function toDateLocale(lang: string | undefined): string {
  return lang === 'fr' ? 'fr-FR' : 'en-US';
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([authInterceptor]),
    ),
    provideTranslateService({ lang: 'en', fallbackLang: 'fr' }),
    provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json', useHttpBackend: true }),
    provideNativeDateAdapter(),
    { provide: MAT_DATE_LOCALE, useFactory: () => toDateLocale(inject(TranslateService).getCurrentLang() ?? undefined) },
    provideAppInitializer(() => inject(AuthService).restoreSession()),
    provideAppInitializer(async () =>
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined'),
    ),
    provideAppInitializer(() => {
      const translate = inject(TranslateService);
      const dateAdapter = inject(DateAdapter);
      translate.onLangChange.subscribe(({ lang }) => dateAdapter.setLocale(toDateLocale(lang)));
    }),
  ],
};
