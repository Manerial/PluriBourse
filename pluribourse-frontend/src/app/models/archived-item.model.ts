import { PageResponse } from './seller.model';

// `price` below: number, not BigDecimal string — see item.model.ts for why.
export interface ArchivedItemDto {
  id: number;
  name: string;
  categoryName: string;
  sold: boolean;
  price: number;
  lotRef: number | null;
  lotName: string | null;
}

export interface ArchivedCatalogFilter {
  name?: string;
  categoryName?: string;
  sold?: boolean;
  page: number;
  size: number;
  sort?: string;
}

export interface ArchivedItemPageResponse {
  page: PageResponse<ArchivedItemDto>;
}
