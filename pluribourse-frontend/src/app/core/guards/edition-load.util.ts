import { Router, UrlTree } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CurrentEditionService } from '../../services/current-edition.service';

/**
 * Shared by every phase route guard (deposit-phase.guard.ts, sale-phase.guard.ts, ...):
 * loads the active edition and returns a `/404` UrlTree if that fails, instead of letting the
 * guard's promise reject. `CurrentEditionService.loadEdition()`'s catchError maps every
 * HttpErrorResponse — including the routine "no active edition" 404 — to EMPTY, so
 * firstValueFrom() rejects with EmptyError rather than resolving; without this, that rejection
 * would surface as an uncaught NavigationError instead of a graceful redirect.
 */
export async function loadEditionOrRedirect(currentEditionService: CurrentEditionService, router: Router): Promise<UrlTree | null> {
  try {
    await firstValueFrom(currentEditionService.loadEdition());
    return null;
  } catch {
    return router.createUrlTree(['/404']);
  }
}
