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
