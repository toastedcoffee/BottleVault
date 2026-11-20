// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import ScanMatchPrompt from './ScanMatchPrompt';
import { brandsApi } from '../../api/brands.api';
import { productsApi } from '../../api/products.api';
import { renderWithQueries, brand, product } from '../../test/catalogFixtures';

vi.mock('../../api/brands.api', () => ({
  brandsApi: { search: vi.fn(), suggest: vi.fn(), getById: vi.fn(), create: vi.fn() },
}));
vi.mock('../../api/products.api', () => ({
  productsApi: { list: vi.fn(), suggest: vi.fn(), getById: vi.fn(), create: vi.fn() },
}));

const buffalo = brand('br-1', 'Buffalo Trace');
const buffaloDistillery = brand('br-2', 'Buffalo Trace Distillery');
const bourbon = product('p-1', 'Kentucky Straight Bourbon', buffalo);

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(brandsApi.search).mockResolvedValue([]);
  vi.mocked(brandsApi.suggest).mockResolvedValue([]);
  vi.mocked(productsApi.suggest).mockResolvedValue([]);
});

describe('ScanMatchPrompt', () => {
  it('offers search hits and near brands for a scanned brand name', async () => {
    vi.mocked(brandsApi.search).mockResolvedValue([buffalo]);
    vi.mocked(brandsApi.suggest).mockResolvedValue([buffaloDistillery, buffalo]);
    const onUseBrand = vi.fn();
    renderWithQueries(
      <ScanMatchPrompt brandName="Buffalo Trace" productName="Bourbon" brandId={null} onUseBrand={onUseBrand} onUseProduct={vi.fn()} />
    );
    expect(await screen.findByText('Did you mean...?')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Buffalo Trace' })).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: 'Buffalo Trace Distillery' }));
    expect(onUseBrand).toHaveBeenCalledWith(buffaloDistillery);
  });

  it('offers near products once the form is on an existing brand', async () => {
    vi.mocked(productsApi.suggest).mockResolvedValue([bourbon]);
    const onUseProduct = vi.fn();
    renderWithQueries(
      <ScanMatchPrompt brandName="Buffalo Trace" productName="Kentucky Straight Burbon" brandId="br-1" onUseBrand={vi.fn()} onUseProduct={onUseProduct} />
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Kentucky Straight Bourbon' }));
    expect(productsApi.suggest).toHaveBeenCalledWith('br-1', 'Kentucky Straight Burbon');
    expect(onUseProduct).toHaveBeenCalledWith(bourbon);
  });

  it('"No, create" on the brand dismisses the prompt', async () => {
    vi.mocked(brandsApi.suggest).mockResolvedValue([buffalo]);
    renderWithQueries(
      <ScanMatchPrompt brandName="Bufalo Trace" productName="Bourbon" brandId={null} onUseBrand={vi.fn()} onUseProduct={vi.fn()} />
    );
    fireEvent.click(await screen.findByRole('button', { name: "No, create 'Bufalo Trace'" }));
    await waitFor(() => expect(screen.queryByText('Did you mean...?')).not.toBeInTheDocument());
    expect(productsApi.suggest).not.toHaveBeenCalled();
  });

  it('moves on to products once a brand is chosen some other way', async () => {
    vi.mocked(brandsApi.suggest).mockResolvedValue([buffalo]);
    vi.mocked(productsApi.suggest).mockResolvedValue([bourbon]);
    const props = { brandName: 'Bufalo Trace', productName: 'Kentucky Straight Burbon', onUseBrand: vi.fn(), onUseProduct: vi.fn() };
    const view = renderWithQueries(<ScanMatchPrompt {...props} brandId={null} />);
    expect(await screen.findByText('The scan names the brand "Bufalo Trace".')).toBeInTheDocument();
    view.rerender(<ScanMatchPrompt {...props} brandId="br-1" />);
    expect(await screen.findByText('The scan names the product "Kentucky Straight Burbon".')).toBeInTheDocument();
    expect(screen.queryByText('The scan names the brand "Bufalo Trace".')).not.toBeInTheDocument();
  });

  it('shows nothing when the catalog has nothing near', async () => {
    renderWithQueries(
      <ScanMatchPrompt brandName="Unheard Of" productName="Thing" brandId={null} onUseBrand={vi.fn()} onUseProduct={vi.fn()} />
    );
    await waitFor(() => expect(brandsApi.suggest).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());
    expect(screen.queryByText('Did you mean...?')).not.toBeInTheDocument();
  });
});
