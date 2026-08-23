import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CurrentEditionService } from '../../services/current-edition.service';
import { loadEditionOrRedirect } from './edition-load.util';

// Blocks a direct/bookmarked navigation to /admin/sellers when there is no active edition,
// instead of leaving the page load a phase-agnostic listing (getSellers has no PhaseGuard, unlike
// create/search) and show an inline error — the sidebar link is itself hidden without an active
// edition (AppLayoutComponent), so reaching this route without one is only possible by URL.
export const activeEditionGuard: CanActivateFn = async () => {
  const currentEditionService = inject(CurrentEditionService);
  const router = inject(Router);
  return (await loadEditionOrRedirect(currentEditionService, router)) ?? true;
};
