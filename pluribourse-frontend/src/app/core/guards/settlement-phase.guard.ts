import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ActivePhase } from '../../models/active-phase.enum';
import { CurrentEditionService } from '../../services/current-edition.service';
import { loadEditionOrRedirect } from './edition-load.util';

// Guards both /volunteer/settlement and /admin/settlement — reversements (story 5.1) are only
// reachable while the edition is in Post-vente, mirroring the backend's
// PhaseGuard.requirePostSalePhase since the client is never trusted alone.
export const settlementPhaseGuard: CanActivateFn = async () => {
  const currentEditionService = inject(CurrentEditionService);
  const router = inject(Router);
  const redirect = await loadEditionOrRedirect(currentEditionService, router);
  if (redirect) {
    return redirect;
  }
  const phase = currentEditionService.currentEdition()?.phase;
  if (phase === ActivePhase.POST_SALE) {
    return true;
  }
  return router.createUrlTree(['/404']);
};
