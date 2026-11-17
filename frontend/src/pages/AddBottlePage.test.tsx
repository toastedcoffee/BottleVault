// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AddBottlePage from './AddBottlePage';
import { useBrands, useCreateBrand } from '../hooks/useBrands';
import { useProducts, useCreateProduct } from '../hooks/useProducts';
import { useCreateBottle, useUploadBottleImage } from '../hooks/useBottles';
import { useBarcodeLookup } from '../hooks/useBarcode';
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';

vi.mock('../hooks/useBrands', () => ({ useBrands: vi.fn(), useCreateBrand: vi.fn() }));
vi.mock('../hooks/useProducts', () => ({ useProducts: vi.fn(), useCreateProduct: vi.fn() }));
vi.mock('../hooks/useBottles', () => ({ useCreateBottle: vi.fn(), useUploadBottleImage: vi.fn() }));
vi.mock('../hooks/useBarcode', () => ({ useBarcodeLookup: vi.fn() }));

// The real scanner needs a camera; this one "scans" a fixed barcode when clicked.
vi.mock('../components/barcode/BarcodeScanner', () => ({
  default: ({ onScan }: { onScan: (barcode: string) => void }) => (
    <button type="button" onClick={() => onScan('082184000427')}>
      Fake scan
    </button>
  ),
}));

const navigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => navigate,
}));

function mutation(result: unknown) {
  return { mutateAsync: vi.fn().mockResolvedValue(result), mutate: vi.fn(), isPending: false };
}

let createBrand: ReturnType<typeof mutation>;
let createProduct: ReturnType<typeof mutation>;
let createBottle: ReturnType<typeof mutation>;

beforeEach(() => {
  vi.clearAllMocks();
  createBrand = mutation({ id: 'br-new' });
  createProduct = mutation({ id: 'p-new' });
  createBottle = mutation({ id: 'b-new' });
  vi.mocked(useBrands).mockReturnValue({ data: [] } as unknown as ReturnType<typeof useBrands>);
  vi.mocked(useProducts).mockReturnValue({ data: [] } as unknown as ReturnType<typeof useProducts>);
  vi.mocked(useCreateBrand).mockReturnValue(createBrand as unknown as ReturnType<typeof useCreateBrand>);
  vi.mocked(useCreateProduct).mockReturnValue(createProduct as unknown as ReturnType<typeof useCreateProduct>);
  vi.mocked(useCreateBottle).mockReturnValue(createBottle as unknown as ReturnType<typeof useCreateBottle>);
  vi.mocked(useUploadBottleImage).mockReturnValue(
    mutation(undefined) as unknown as ReturnType<typeof useUploadBottleImage>
  );
  vi.mocked(useBarcodeLookup).mockReturnValue(
    mutation(undefined) as unknown as ReturnType<typeof useBarcodeLookup>
  );
});

/** Opens "new product" with "new brand", fills the brand, and returns the form. */
function startNewProductWithNewBrand() {
  render(
    <MemoryRouter>
      <AddBottlePage />
    </MemoryRouter>
  );
  fireEvent.click(screen.getByRole('button', { name: /add new product/i }));
  fireEvent.click(screen.getByRole('button', { name: /add new brand/i }));
  fireEvent.change(screen.getByPlaceholderText('Brand name'), { target: { value: 'Acme Distilling' } });
}

const submit = () => fireEvent.click(screen.getByRole('button', { name: /add to collection/i }));

describe('AddBottlePage validation order', () => {
  it('does not create a brand when the product name is missing', async () => {
    startNewProductWithNewBrand();
    submit();
    expect(await screen.findByText('Product name is required')).toBeInTheDocument();
    // The catalog is shared: a brand created before a rejected product is an orphan.
    expect(createBrand.mutateAsync).not.toHaveBeenCalled();
    expect(createProduct.mutateAsync).not.toHaveBeenCalled();
  });

  it('does not create a brand when the barcode is not a product barcode', async () => {
    startNewProductWithNewBrand();
    fireEvent.change(screen.getByLabelText('Product Name'), { target: { value: 'Small Batch' } });
    fireEvent.change(screen.getByLabelText('Barcode'), { target: { value: 'https://example.com' } });
    submit();
    expect(
      await screen.findByText('Barcode must be 8 to 14 digits (the UPC/EAN number under the bars)')
    ).toBeInTheDocument();
    expect(createBrand.mutateAsync).not.toHaveBeenCalled();
    expect(createProduct.mutateAsync).not.toHaveBeenCalled();
  });

  it('saves a barcode copied with spaces and dashes as plain digits', async () => {
    startNewProductWithNewBrand();
    fireEvent.change(screen.getByLabelText('Product Name'), { target: { value: 'Small Batch' } });
    fireEvent.change(screen.getByLabelText('Barcode'), { target: { value: '0 82184-00042 7' } });
    submit();
    await waitFor(() => expect(createProduct.mutateAsync).toHaveBeenCalled());
    expect(createBrand.mutateAsync).toHaveBeenCalledTimes(1);
    expect(createProduct.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Small Batch', brandId: 'br-new', barcode: '082184000427' })
    );
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/inventory'));
  });

  it('leaves the barcode out when the field is empty', async () => {
    startNewProductWithNewBrand();
    fireEvent.change(screen.getByLabelText('Product Name'), { target: { value: 'Small Batch' } });
    submit();
    await waitFor(() => expect(createProduct.mutateAsync).toHaveBeenCalled());
    expect(createProduct.mutateAsync).toHaveBeenCalledWith(expect.objectContaining({ barcode: undefined }));
  });
});

function axiosError(status: number, data: unknown = {}): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig;
  const response = { status, data, headers: {}, statusText: '', config } as AxiosResponse;
  return new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', config, null, response);
}

describe('AddBottlePage error messages', () => {
  it("shows the server's reason when the bottle is refused", async () => {
    startNewProductWithNewBrand();
    fireEvent.change(screen.getByLabelText('Product Name'), { target: { value: 'Small Batch' } });
    createBottle.mutateAsync.mockRejectedValue(axiosError(400, { message: 'notes: must be at most 5000 characters' }));
    submit();
    expect(await screen.findByText('notes: must be at most 5000 characters')).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('opens the edit page with the reason when the bottle saved but its photo did not', async () => {
    const upload = mutation(undefined);
    upload.mutateAsync.mockRejectedValue(axiosError(415, { message: 'Only JPEG, PNG and WebP photos are supported.' }));
    vi.mocked(useUploadBottleImage).mockReturnValue(upload as unknown as ReturnType<typeof useUploadBottleImage>);
    startNewProductWithNewBrand();
    fireEvent.change(screen.getByLabelText('Product Name'), { target: { value: 'Small Batch' } });
    fireEvent.change(screen.getByLabelText('Photo'), {
      target: { files: [new File(['x'], 'label.gif', { type: 'image/gif' })] },
    });
    submit();
    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith('/inventory/b-new/edit', {
        state: { notice: 'Bottle saved, but the photo was not: Only JPEG, PNG and WebP photos are supported.' },
      })
    );
  });
});

describe('AddBottlePage barcode lookup errors', () => {
  it("shows the server's sentence when today's lookup budget is spent", async () => {
    const lookup = mutation(undefined);
    const message =
      "You've reached today's limit of 30 barcode lookups. Enter the details by hand, or try again tomorrow.";
    lookup.mutate.mockImplementation((_barcode: string, options: { onError: (err: unknown) => void }) => {
      const config = { headers: {} } as InternalAxiosRequestConfig;
      const response = {
        status: 429, data: { message }, headers: { 'retry-after': '82800' }, statusText: '', config,
      } as AxiosResponse;
      options.onError(new AxiosError('Request failed with status code 429', 'ERR_BAD_REQUEST', config, null, response));
    });
    vi.mocked(useBarcodeLookup).mockReturnValue(lookup as unknown as ReturnType<typeof useBarcodeLookup>);
    render(
      <MemoryRouter>
        <AddBottlePage />
      </MemoryRouter>
    );
    fireEvent.click(screen.getByRole('button', { name: /^scan$/i }));
    fireEvent.click(await screen.findByRole('button', { name: 'Fake scan' }));
    expect(await screen.findByText(message)).toBeInTheDocument();
  });
});
