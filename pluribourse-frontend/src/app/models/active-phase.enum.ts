import { PhaseType } from './edition.model';

// Every non-terminal phase, in state-machine order — CLOSED is appended separately below for
// ALL_PHASES. Despite the name, this no longer mirrors the backend's PhaseType.ACTIVE since
// Story 2.10 (which excludes PREPARATION) — see CurrentEditionService.ACTIVE_PHASES for that.
export enum ActivePhase {
  PREPARATION = 'PREPARATION',
  DEPOSIT = 'DEPOSIT',
  SALE = 'SALE',
  POST_SALE = 'POST_SALE'
}

// Mirrors PhaseType on the backend — ActivePhase plus the terminal CLOSED phase.
export const ALL_PHASES: readonly PhaseType[] = [...Object.values(ActivePhase), 'CLOSED'];

// Where a volunteer should land for the given phase. In Préparation there's nothing to deposit or
// sell yet, so the landing page is printer selection instead. No volunteer page exists yet for
// Clôturée, which falls through to the app's wildcard 404 route.
// Used both by the initial /volunteer redirect and by the reactive redirect that fires when
// the edition's phase changes while a volunteer is already on a page (see AppLayoutComponent).
// Story 5.1 (2026-08-14): Post-vente now lands on /volunteer/settlement rather than
// /volunteer/deposit (story 3.6's deposit slip reprint landing) — volunteers have no navigation
// menu, so the reversements page carries an explicit link back to /volunteer/deposit for that
// reprint flow instead. /volunteer/deposit itself stays reachable in Post-vente
// (depositPhaseGuard is unchanged), just no longer the automatic landing target.
export function resolveVolunteerLandingPath(phase: PhaseType | undefined): string {
  if (phase === ActivePhase.PREPARATION) {
    return '/printer-selection';
  }
  if (phase === ActivePhase.DEPOSIT) {
    return '/volunteer/deposit';
  }
  if (phase === ActivePhase.SALE) {
    return '/volunteer/pos';
  }
  if (phase === ActivePhase.POST_SALE) {
    return '/volunteer/settlement';
  }
  return '/404';
}
