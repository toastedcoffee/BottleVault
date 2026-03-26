import type { ProductResponse } from './product';

export interface BarcodeLookupResponse {
  found: boolean;
  source: string | null;
  product: ProductResponse | null;
  externalProduct: ExternalProductData | null;
}

export interface ExternalProductData {
  name: string;
  brandName: string | null;
  barcode: string;
  size: string | null;
  abv: number | null;
  imageUrl: string | null;
  categories: string | null;
}
