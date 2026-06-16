import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError(error => {
      if (error.status === 401) {
        auth.currentUser.set(null);
        router.navigate(['/login']);
      } else if (error.status === 403 && error.error?.type?.includes('password-change-required')) {
        router.navigate(['/change-password']);
      } else if (error.status === 403) {
        auth.currentUser.set(null);
        router.navigate(['/login']);
      }
      return throwError(() => error);
    })
  );
};
