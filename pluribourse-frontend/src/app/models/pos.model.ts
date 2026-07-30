export interface ScanResult {
  itemId: number;
  name: string;
  price: number | null;
  incomplete: boolean;
  comment: string | null;
}

export type PaymentMethod = 'CASH' | 'CHECK' | 'CARD';

export interface Basket {
  id: number;
  items: ScanResult[];
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
