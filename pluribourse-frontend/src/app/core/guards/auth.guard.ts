import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

export const authGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  if (auth.currentUser()?.forcePasswordChange && route.routeConfig?.path !== 'change-password') {
    return router.createUrlTree(['/change-password']);
  }
  if (
    auth.currentUser()?.role === 'VOLUNTEER' &&
    !auth.printerSelectionDone() &&
    route.routeConfig?.path !== 'printer-selection'
  ) {
    return router.createUrlTree(['/printer-selection']);
  }
  return true;
};
