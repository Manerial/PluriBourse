export interface ScanResult {
  itemId: number;
  name: string;
  price: number | null;
  incomplete: boolean;
  comment: string | null;
}
