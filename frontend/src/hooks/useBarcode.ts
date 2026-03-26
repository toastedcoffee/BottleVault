import { useMutation } from '@tanstack/react-query';
import { barcodeApi } from '../api/barcode.api';

export function useBarcodeLookup() {
  return useMutation({
    mutationFn: (barcode: string) => barcodeApi.lookup(barcode),
  });
}
