import { PhaseType } from './edition.model';

// Mirrors PhaseType.ACTIVE on the backend (org.pluribourse.edition.entity.PhaseType).
export enum ActivePhase {
  PREPARATION = 'PREPARATION',
  DEPOSIT = 'DEPOSIT',
  SALE = 'SALE',
  POST_SALE = 'POST_SALE'
}

// Mirrors PhaseType on the backend — ActivePhase plus the terminal CLOSED phase.
export const ALL_PHASES: readonly PhaseType[] = [...Object.values(ActivePhase), 'CLOSED'];

// Where a volunteer should land for the given phase. The seller file page also stays reachable
// in Post-vente (story 3.6, deposit slip reprint) — no volunteer page exists yet for
// Préparation/Clôturée, those fall through to the app's wildcard 404 route.
// Used both by the initial /volunteer redirect and by the reactive redirect that fires when
// the edition's phase changes while a volunteer is already on a page (see AppLayoutComponent) —
// without POST_SALE here, that reactive redirect would bounce a volunteer off
// /volunteer/deposit to /404 the instant the phase advances past Dépôt, defeating AC 7.
export function resolveVolunteerLandingPath(phase: PhaseType | undefined): string {
  if (phase === ActivePhase.DEPOSIT || phase === ActivePhase.POST_SALE) {
    return '/volunteer/deposit';
  }
  if (phase === ActivePhase.SALE) {
    return '/volunteer/pos';
  }
  return '/404';
}
