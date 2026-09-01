// Mirrors org.pluribourse.domain.payout.entity.SettlementStatus (backend) — UNSETTLED is
// never persisted there either, it's the "no Settlement row" case.
export type SettlementStatus = 'UNSETTLED' | 'SETTLED' | 'UNCLAIMED';

export type StatusFilter = 'all' | 'unsettled' | 'settled';

export interface SettlementDto {
  sellerId: number;
  firstName: string;
  lastName: string;
  phone: string;
  email: string;
  amountDue: number;
  amountPaid: number | null;
  status: SettlementStatus;
}

export interface SettleRequest {
  amount: number;
}

// SSE payload broadcast by the backend after a settle/markUnclaimed commits (story 5.7). Kept
// here rather than in edition.model.ts alongside the other *Event interfaces because it is a
// settlement event, not an edition-lifecycle one — look for it next to SettlementDto if you
// expected it there.
export interface SettlementUpdatedEvent {
  editionId: number;
  sellerId: number;
}

export interface BulkSettlementReportPrintResultDto {
  succeededCount: number;
  failedCount: number;
}
