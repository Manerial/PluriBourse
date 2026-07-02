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

// Where a volunteer should land for the given phase. No volunteer page exists yet for
// Préparation/Vente/Post-vente/Clôturée — those fall through to the app's wildcard 404 route.
// Used both by the initial /volunteer redirect and by the reactive redirect that fires when
// the edition's phase changes while a volunteer is already on a page (see AppLayoutComponent).
export function resolveVolunteerLandingPath(phase: PhaseType | undefined): string {
  return phase === ActivePhase.DEPOSIT ? '/volunteer/deposit' : '/404';
}
