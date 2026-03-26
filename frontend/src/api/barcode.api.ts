import apiClient from './client';
import type { BarcodeLookupResponse } from '../types/barcode';

export const barcodeApi = {
  lookup: (barcode: string) =>
    apiClient.get<BarcodeLookupResponse>(`/barcode/${barcode}`).then((r) => r.data),
};
