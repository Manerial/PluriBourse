import { ItemDto } from './item.model';

export interface CreateLotItemRequest {
  categoryId: number;
  name: string;
  incomplete: boolean;
  comment: string | null;
}

export interface CreateLotRequest {
  sellerProfileId: number;
  name: string;
  globalPrice: number;
  items: CreateLotItemRequest[];
}

export interface LotDto {
  id: number;
  name: string;
  globalPrice: number;
  items: ItemDto[];
}
