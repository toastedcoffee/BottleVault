// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach, type Mock } from 'vitest';
import type { ProductResponse } from '../../types/product';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import ProductPicker from './ProductPicker';
import { productsApi } from '../../api/products.api';
import { renderWithQueries, brand, product } from '../../test/catalogFixtures';

vi.mock('../../api/products.api', () => ({
  productsApi: { list: vi.fn(), suggest: vi.fn(), getById: vi.fn(), create: vi.fn() },
}));

const acme = brand('br-1', 'Acme');
const smallBatch = product('p-1', 'Small Batch', acme, '750ml');
const caskStrength = product('p-2', 'Cask Strength', acme);

let onChange: Mock<(item: ProductResponse | null) => void>;
let onCreateNew: Mock<(name: string) => void>;

beforeEach(() => {
  vi.clearAllMocks();
  onChange = vi.fn();
  onCreateNew = vi.fn();
  vi.mocked(productsApi.list).mockResolvedValue([smallBatch, caskStrength]);
  vi.mocked(productsApi.suggest).mockResolvedValue([]);
});

function renderPicker() {
  renderWithQueries(<ProductPicker brandId="br-1" value="" onChange={onChange} onCreateNew={onCreateNew} />);
}

const type = (text: string) => fireEvent.change(screen.getByLabelText('Product'), { target: { value: text } });

describe('ProductPicker', () => {
  it("lists the brand's products before anything is typed", async () => {
    renderPicker();
    expect(await screen.findByRole('button', { name: 'Small Batch (750ml)' })).toBeInTheDocument();
    expect(productsApi.list).toHaveBeenCalledWith({ brandId: 'br-1' });
    expect(screen.queryByRole('button', { name: /create new product/i })).not.toBeInTheDocument();
  });

  it('narrows by the typed term and reports the pick', async () => {
    renderPicker();
    vi.mocked(productsApi.list).mockResolvedValue([caskStrength]);
    type('cask');
    fireEvent.click(await screen.findByRole('button', { name: 'Cask Strength' }));
    expect(productsApi.list).toHaveBeenCalledWith({ brandId: 'br-1', search: 'cask' });
    expect(onChange).toHaveBeenCalledWith(caskStrength);
  });

  it('create new -> did you mean -> No, create', async () => {
    vi.mocked(productsApi.suggest).mockResolvedValue([smallBatch]);
    renderPicker();
    type('Smal Batch');
    fireEvent.click(await screen.findByRole('button', { name: 'Create new product "Smal Batch"' }));
    expect(await screen.findByText('Did you mean...?')).toBeInTheDocument();
    expect(productsApi.suggest).toHaveBeenCalledWith('br-1', 'Smal Batch');
    fireEvent.click(screen.getByRole('button', { name: "No, create 'Smal Batch'" }));
    expect(onCreateNew).toHaveBeenCalledWith('Smal Batch');
  });

  it('picking a suggestion selects the existing product instead of creating', async () => {
    vi.mocked(productsApi.suggest).mockResolvedValue([smallBatch]);
    renderPicker();
    type('Smal Batch');
    fireEvent.click(await screen.findByRole('button', { name: 'Create new product "Smal Batch"' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Small Batch (750ml)' }));
    expect(onChange).toHaveBeenCalledWith(smallBatch);
    await waitFor(() => expect(onCreateNew).not.toHaveBeenCalled());
  });
});
