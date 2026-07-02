export interface SellerDto {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
}

export interface CreateSellerRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
