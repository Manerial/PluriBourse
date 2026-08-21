import { PageResponse } from './seller.model';

export interface ArchivedItemDto {
  id: number;
  name: string;
  categoryName: string;
  sold: boolean;
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
