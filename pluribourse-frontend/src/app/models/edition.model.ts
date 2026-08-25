import { Language } from './language.enum';

export type PhaseType = 'PREPARATION' | 'DEPOSIT' | 'SALE' | 'POST_SALE' | 'CLOSED';

export interface PhaseChangedEvent {
  editionId: number;
  newPhase: PhaseType;
  previousPhase: PhaseType;
}

export interface BasketCancelledEvent {
  editionId: number;
  newPhase: PhaseType;
}

export interface EditionDto {
  id: number;
  name: string;
  phase: PhaseType;
  commissionRate: number;
  documentLanguage: Language;
  createdAt: string; // ISO 8601 date string "YYYY-MM-DD"
  archived: boolean;
  startDate: string;
  endDate: string;
  hasItems?: boolean;
  currency: string;
}
