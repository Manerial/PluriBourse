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

export interface BulkSettlementReportPrintResultDto {
  succeededCount: number;
  failedCount: number;
}
