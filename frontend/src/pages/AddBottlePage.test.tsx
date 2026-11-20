// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AddBottlePage from './AddBottlePage';
import { useCreateBrand } from '../hooks/useBrands';
import { useCreateProduct } from '../hooks/useProducts';
import { useCreateBottle, useUploadBottleImage } from '../hooks/useBottles';
import { useBarcodeLookup } from '../hooks/useBarcode';
import { brandsApi } from '../api/brands.api';
import { productsApi } from '../api/products.api';
import { renderWithQueries, brand, product } from '../test/catalogFixtures';
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';

// The pickers' searches run for real over mocked API calls; only the writes are mocked hooks.
vi.mock('../hooks/useBrands', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../hooks/useBrands')>()),
  useCreateBrand: vi.fn(),
}));
vi.mock('../hooks/useProducts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../hooks/useProducts')>()),
  useCreateProduct: vi.fn(),
}));
vi.mock('../api/brands.api', () => ({
  brandsApi: { search: vi.fn(), suggest: vi.fn(), getById: vi.fn(), create: vi.fn() },
}));
vi.mock('../api/products.api', () => ({
  productsApi: { list: vi.fn(), suggest: vi.fn(), getById: vi.fn(), create: vi.fn() },
}));
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
  vi.mocked(brandsApi.search).mockResolvedValue([]);
  vi.mocked(brandsApi.suggest).mockResolvedValue([]);
  vi.mocked(productsApi.list).mockResolvedValue([]);
  vi.mocked(productsApi.suggest).mockResolvedValue([]);
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

function renderPage() {
  renderWithQueries(
    <MemoryRouter>
      <AddBottlePage />
    </MemoryRouter>
  );
}

/**
 * Searches the brand picker for a name the catalog lacks and chooses "Create new",
 * which (with nothing near it) opens the new-product form with that new brand.
 */
async function startNewProductWithNewBrand() {
  renderPage();
  fireEvent.change(screen.getByLabelText('Brand'), { target: { value: 'Acme Distilling' } });
  fireEvent.click(await screen.findByRole('button', { name: 'Create new brand "Acme Distilling"' }));
  expect(await screen.findByPlaceholderText('Brand name')).toHaveValue('Acme Distilling');
}

const submit = () => fireEvent.click(screen.getByRole('button', { name: /add to collection/i }));

describe('AddBottlePage validation order', () => {
  it('does not create a brand when the product name is missing', async () => {
    await startNewProductWithNewBrand();
    submit();
    expect(await screen.findByText('Product name is required')).toBeInTheDocument();
    // The catalog is shared: a brand created before a rejected product is an orphan.
    expect(createBrand.mutateAsync).not.toHaveBeenCalled();
    expect(createProduct.mutateAsync).not.toHaveBeenCalled();
  });

  it('does not create a brand when the barcode is not a product barcode', async () => {
    await startNewProductWithNewBrand();
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
    await startNewProductWithNewBrand();
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
    await startNewProductWithNewBrand();
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
    await startNewProductWithNewBrand();
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
    await startNewProductWithNewBrand();
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
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /^scan$/i }));
    fireEvent.click(await screen.findByRole('button', { name: 'Fake scan' }));
    expect(await screen.findByText(message)).toBeInTheDocument();
  });
});

describe('AddBottlePage catalog pickers', () => {
  const acme = brand('br-1', 'Acme Distilling');
  const smallBatch = product('p-1', 'Small Batch', acme);

  it('searches brands as you type and picks a product from the chosen brand', async () => {
    vi.mocked(brandsApi.search).mockResolvedValue([acme]);
    vi.mocked(productsApi.list).mockResolvedValue([smallBatch]);
    renderPage();
    fireEvent.change(screen.getByLabelText('Brand'), { target: { value: 'acme' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Acme Distilling' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Small Batch' }));
    submit();
    await waitFor(() => expect(createBottle.mutateAsync).toHaveBeenCalledWith(expect.objectContaining({ productId: 'p-1' })));
    expect(createBrand.mutateAsync).not.toHaveBeenCalled();
    expect(createProduct.mutateAsync).not.toHaveBeenCalled();
  });

  it('create new -> did you mean -> picking the suggestion uses the existing brand', async () => {
    vi.mocked(brandsApi.suggest).mockResolvedValue([acme]);
    vi.mocked(productsApi.list).mockResolvedValue([smallBatch]);
    renderPage();
    fireEvent.change(screen.getByLabelText('Brand'), { target: { value: 'Acme Distiling' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Create new brand "Acme Distiling"' }));
    expect(await screen.findByText('Did you mean...?')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Acme Distilling' }));
    expect(await screen.findByRole('button', { name: 'Small Batch' })).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('Brand name')).not.toBeInTheDocument();
  });

  it("create new -> did you mean -> No, create opens the new brand with the typed name", async () => {
    vi.mocked(brandsApi.suggest).mockResolvedValue([acme]);
    renderPage();
    fireEvent.change(screen.getByLabelText('Brand'), { target: { value: 'Acme Distiling' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Create new brand "Acme Distiling"' }));
    fireEvent.click(await screen.findByRole('button', { name: "No, create 'Acme Distiling'" }));
    expect(await screen.findByPlaceholderText('Brand name')).toHaveValue('Acme Distiling');
  });

  it('a scan that pre-fills brand and product names asks "did you mean" for both, then joins the existing product', async () => {
    const lookup = mutation(undefined);
    lookup.mutate.mockImplementation((_barcode: string, options: { onSuccess: (r: unknown) => void }) => {
      options.onSuccess({
        found: true, source: 'provider', product: null,
        externalProduct: {
          name: 'Smal Batch', brandName: 'Acme Distiling', barcode: '082184000427',
          size: null, abv: null, imageUrl: null, categories: null,
        },
      });
    });
    vi.mocked(useBarcodeLookup).mockReturnValue(lookup as unknown as ReturnType<typeof useBarcodeLookup>);
    vi.mocked(brandsApi.suggest).mockResolvedValue([acme]);
    vi.mocked(brandsApi.getById).mockResolvedValue(acme);
    vi.mocked(productsApi.suggest).mockResolvedValue([smallBatch]);
    createProduct.mutateAsync.mockResolvedValue({ id: 'p-1' });

    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /^scan$/i }));
    fireEvent.click(await screen.findByRole('button', { name: 'Fake scan' }));

    expect(await screen.findByText(`The scan names the brand "Acme Distiling".`)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Acme Distilling' }));
    expect(await screen.findByText(`The scan names the product "Smal Batch".`)).toBeInTheDocument();
    expect(productsApi.suggest).toHaveBeenCalledWith('br-1', 'Smal Batch');
    fireEvent.click(screen.getByRole('button', { name: 'Small Batch' }));
    expect(screen.getByLabelText('Product Name')).toHaveValue('Small Batch');

    submit();
    await waitFor(() => expect(createProduct.mutateAsync).toHaveBeenCalled());
    expect(createBrand.mutateAsync).not.toHaveBeenCalled();
    expect(createProduct.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Small Batch', brandId: 'br-1', barcode: '082184000427' })
    );
  });
});
