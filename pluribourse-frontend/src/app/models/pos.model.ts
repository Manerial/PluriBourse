import { PageResponse } from './seller.model';

export interface ScanResult {
  itemId: number;
  name: string;
  price: number | null;
  incomplete: boolean;
  comment: string | null;
  lotId: number | null;
}

export interface LotGroup {
  lotId: number;
  lotName: string;
  globalPrice: number;
  scannedCount: number;
  totalCount: number;
}

export type PaymentMethod = 'CASH' | 'CHECK' | 'CARD';

export interface Basket {
  id: number;
  items: ScanResult[];
  lotGroups: LotGroup[];
  total: number;
}

export interface ValidateBasketRequest {
  paymentMethod: PaymentMethod;
  amountGiven: number | null;
}

/**
 * What the payment dialog hands back on confirm. `printInvoice` is a UI choice only — it must
 * never travel in the body of POST /pos/baskets/{id}/validate (story 4.7 AC 4).
 */
export interface PaymentDialogResult {
  request: ValidateBasketRequest;
  printInvoice: boolean;
}

export interface Sale {
  id: number;
  total: number;
  paymentMethod: PaymentMethod;
  amountGiven: number | null;
  changeDue: number | null;
}

/** One row of the "sales list" screen (story 4.7, FR-108). */
export interface SaleListItem {
  id: number;
  soldAt: string;
  cashier: string;
  paymentMethod: PaymentMethod;
  total: number;
  currency: string;
}

export interface SaleListFilter {
  dateFrom?: string;
  dateTo?: string;
  cashier?: string;
  page: number;
  size: number;
  sort?: string;
}

export interface SaleListPageResponse {
  page: PageResponse<SaleListItem>;
}
