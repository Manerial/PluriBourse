import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ActivePhase } from '../../models/active-phase.enum';
import { CurrentEditionService } from '../../services/current-edition.service';

// Blocks a direct/bookmarked navigation to /volunteer/deposit outside the Deposit phase (AC 7)
// instead of relying solely on AppLayoutComponent's reactive redirect, which only fires on a
// phase *change* and would otherwise let the page render briefly before redirecting away.
export const depositPhaseGuard: CanActivateFn = async () => {
  const currentEditionService = inject(CurrentEditionService);
  const router = inject(Router);
  await firstValueFrom(currentEditionService.loadEdition());
  if (currentEditionService.currentEdition()?.phase === ActivePhase.DEPOSIT) {
    return true;
  }
  return router.createUrlTree(['/404']);
};
