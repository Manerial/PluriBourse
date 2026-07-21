import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ActivePhase } from '../../models/active-phase.enum';
import { CurrentEditionService } from '../../services/current-edition.service';

// Blocks a direct/bookmarked navigation to /volunteer/deposit outside the Deposit/Post-vente
// phases (AC 7 of story 3.9, extended by story 3.6 to also allow Post-vente for deposit slip
// reprinting) instead of relying solely on AppLayoutComponent's reactive redirect, which only
// fires on a phase *change* and would otherwise let the page render briefly before redirecting away.
export const depositPhaseGuard: CanActivateFn = async () => {
  const currentEditionService = inject(CurrentEditionService);
  const router = inject(Router);
  await firstValueFrom(currentEditionService.loadEdition());
  const phase = currentEditionService.currentEdition()?.phase;
  if (phase === ActivePhase.DEPOSIT || phase === ActivePhase.POST_SALE) {
    return true;
  }
  return router.createUrlTree(['/404']);
};
