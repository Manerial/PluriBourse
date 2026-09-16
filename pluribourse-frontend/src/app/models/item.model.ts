import { PageResponse } from './seller.model';

// `price` (and every other monetary field across the DTOs in this directory) is a plain `number`,
// not a numeric string: Jackson serializes the backend's `BigDecimal` straight to a JSON number,
// and the frontend only ever displays/forwards these values — it never performs arithmetic on
// them, so the float-precision risk of `number` never materializes. CLAUDE.md's BigDecimal-only
// rule is scoped to backend calculations, not to how amounts travel over the wire.
export interface ItemDto {
  id: number;
  sellerProfileId: number;
  categoryId: number;
  categoryName: string;
  name: string;
  price: number | null;
  incomplete: boolean;
  comment: string | null;
  tableNumber: number;
  lotId: number | null;
  lotName: string | null;
  lotPrice: number | null;
}

export interface CreateItemRequest {
  sellerProfileId: number;
  categoryId: number;
  name: string;
  price: number;
  incomplete: boolean;
  comment: string | null;
}

export interface ItemCompletenessRequest {
  incomplete: boolean;
  comment: string | null;
}

export interface ItemCatalogDto {
  id: number;
  barcode: string;
  name: string;
  price: number | null;
  incomplete: boolean;
  sold: boolean;
  categoryName: string;
  tableNumber: number;
  sellerFirstName: string;
  sellerLastName: string;
  lotId: number | null;
  lotName: string | null;
  lotPrice: number | null;
}

export interface CatalogFilter {
  name?: string;
  barcode?: string;
  categoryId?: number;
  tableNumber?: number;
  sold?: boolean;
  incomplete?: boolean;
  sellerName?: string;
  page: number;
  size: number;
  sort?: string;
}

export interface ItemCatalogPageResponse {
  page: PageResponse<ItemCatalogDto>;
}
