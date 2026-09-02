import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ActivePhase } from '../../models/active-phase.enum';
import { CurrentEditionService } from '../../services/current-edition.service';
import { loadEditionOrRedirect } from './edition-load.util';

// Blocks a direct/bookmarked navigation to /volunteer/deposit outside the Deposit phase (AC 7 of
// story 3.9; story 5.8 removed the Post-vente allowance that story 3.6 had added for deposit slip
// reprinting) instead of relying solely on AppLayoutComponent's reactive redirect, which only
// fires on a phase *change* and would otherwise let the page render briefly before redirecting away.
export const depositPhaseGuard: CanActivateFn = async () => {
  const currentEditionService = inject(CurrentEditionService);
  const router = inject(Router);
  const redirect = await loadEditionOrRedirect(currentEditionService, router);
  if (redirect) {
    return redirect;
  }
  const phase = currentEditionService.currentEdition()?.phase;
  if (phase === ActivePhase.DEPOSIT) {
    return true;
  }
  return router.createUrlTree(['/404']);
};
