export interface BrandResponse {
  id: string;
  name: string;
  country: string | null;
  website: string | null;
}

export interface BrandCreateRequest {
  name: string;
  country?: string;
  website?: string;
}
