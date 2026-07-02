import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { extractErrorType } from '../../shared/http-error.util';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        auth.clearSession();
        router.navigate(['/login']);
      } else if (error.status === 403 && extractErrorType(error)?.includes('password-change-required')) {
        router.navigate(['/change-password']);
      } else if (error.status === 403) {
        auth.clearSession();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    })
  );
};
