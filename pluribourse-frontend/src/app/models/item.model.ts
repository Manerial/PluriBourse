import { PageResponse } from './seller.model';

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
