import type { ProductResponse } from './product';

export type BottleStatus = 'UNOPENED' | 'OPENED' | 'EMPTY';

export interface BottleResponse {
  id: string;
  status: BottleStatus;
  percentageLeft: number;
  purchaseDate: string | null;
  purchaseLocation: string | null;
  purchaseCost: number | null;
  notes: string | null;
  rating: number | null;
  storageLocation: string | null;
  imagePath: string | null;
  createdAt: string;
  updatedAt: string;
  product: ProductResponse;
}

export interface BottleCreateRequest {
  productId: string;
  status?: BottleStatus;
  percentageLeft?: number;
  purchaseDate?: string;
  purchaseLocation?: string;
  purchaseCost?: number;
  notes?: string;
  rating?: number;
  storageLocation?: string;
}

export interface BottleUpdateRequest {
  status?: BottleStatus;
  percentageLeft?: number;
  purchaseDate?: string;
  purchaseLocation?: string;
  purchaseCost?: number;
  notes?: string;
  rating?: number;
  storageLocation?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
