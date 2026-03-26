import type { BrandResponse } from './brand';

export type AlcoholType =
  | 'WHISKEY' | 'BOURBON' | 'SCOTCH' | 'RYE' | 'VODKA' | 'GIN' | 'RUM'
  | 'TEQUILA' | 'BRANDY' | 'COGNAC'
  | 'WINE_RED' | 'WINE_WHITE' | 'WINE_ROSE' | 'WINE_SPARKLING' | 'WINE_DESSERT'
  | 'BEER' | 'IPA' | 'STOUT' | 'LAGER' | 'PILSNER' | 'WHEAT_BEER'
  | 'LIQUEUR' | 'AMARO' | 'VERMOUTH' | 'ABSINTHE' | 'MEZCAL' | 'SAKE' | 'OTHER';

export interface ProductResponse {
  id: string;
  name: string;
  barcode: string | null;
  type: AlcoholType;
  subtype: string | null;
  size: string | null;
  abv: number | null;
  description: string | null;
  imageUrl: string | null;
  isUserCreated: boolean;
  brand: BrandResponse;
}

export interface ProductCreateRequest {
  name: string;
  brandId: string;
  type: AlcoholType;
  barcode?: string;
  subtype?: string;
  size?: string;
  abv?: number;
  description?: string;
  imageUrl?: string;
}
