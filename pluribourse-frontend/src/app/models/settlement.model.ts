// Mirrors org.pluribourse.domain.payout.entity.SettlementStatus (backend) — UNSETTLED is
// never persisted there either, it's the "no Settlement row" case.
export type SettlementStatus = 'UNSETTLED' | 'SETTLED' | 'UNCLAIMED';

export interface SettlementDto {
  sellerId: number;
  firstName: string;
  lastName: string;
  phone: string;
  email: string;
  amountDue: number;
  status: SettlementStatus;
}

export interface SettleRequest {
  amount: number;
}
