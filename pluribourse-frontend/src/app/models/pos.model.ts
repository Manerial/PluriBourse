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

export interface Sale {
  id: number;
  total: number;
  paymentMethod: PaymentMethod;
  amountGiven: number | null;
  changeDue: number | null;
}
