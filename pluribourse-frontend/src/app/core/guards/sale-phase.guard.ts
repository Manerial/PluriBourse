import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ActivePhase } from '../../models/active-phase.enum';
import { CurrentEditionService } from '../../services/current-edition.service';
import { loadEditionOrRedirect } from './edition-load.util';

// Blocks a direct/bookmarked navigation to /volunteer/pos outside the Sale phase (AC 8 of
// story 4.1) instead of relying solely on AppLayoutComponent's reactive redirect, which only
// fires on a phase *change* and would otherwise let the page render briefly before redirecting away.
export const salePhaseGuard: CanActivateFn = async () => {
  const currentEditionService = inject(CurrentEditionService);
  const router = inject(Router);
  const redirect = await loadEditionOrRedirect(currentEditionService, router);
  if (redirect) {
    return redirect;
  }
  const phase = currentEditionService.currentEdition()?.phase;
  if (phase === ActivePhase.SALE) {
    return true;
  }
  return router.createUrlTree(['/404']);
};
