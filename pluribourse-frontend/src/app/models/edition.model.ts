export type PhaseType = 'PREPARATION' | 'DEPOSIT' | 'SALE' | 'POST_SALE' | 'CLOSED';

export interface EditionDto {
  id: number;
  name: string;
  phase: PhaseType;
  commissionRate: number;
  documentLanguage: 'EN' | 'FR';
  createdAt: string; // ISO 8601 date string "YYYY-MM-DD"
  archived: boolean;
  startDate: string | null;
  endDate: string | null;
}
