export interface ItemDto {
  id: number;
  sellerProfileId: number;
  categoryId: number;
  categoryName: string;
  name: string;
  price: number;
  incomplete: boolean;
  comment: string | null;
  tableNumber: number;
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
