import { ItemDto } from './item.model';

export interface CreateLotItemRequest {
  name: string;
  incomplete: boolean;
  comment: string | null;
}

export interface CreateLotRequest {
  sellerProfileId: number;
  categoryId: number;
  name: string;
  globalPrice: number;
  items: CreateLotItemRequest[];
}

export interface UpdateLotItemRequest {
  id: number | null;
  name: string;
  incomplete: boolean;
  comment: string | null;
}

export interface UpdateLotRequest {
  categoryId: number;
  name: string;
  globalPrice: number;
  items: UpdateLotItemRequest[];
}

export interface LotDto {
  id: number;
  name: string;
  globalPrice: number;
  categoryId: number;
  categoryName: string;
  items: ItemDto[];
}
