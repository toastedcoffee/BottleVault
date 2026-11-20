// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach, type Mock } from 'vitest';
import type { BrandResponse } from '../../types/brand';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import BrandPicker from './BrandPicker';
import { brandsApi } from '../../api/brands.api';
import { renderWithQueries, brand } from '../../test/catalogFixtures';

vi.mock('../../api/brands.api', () => ({
  brandsApi: { search: vi.fn(), suggest: vi.fn(), getById: vi.fn(), create: vi.fn() },
}));

const jameson = brand('br-1', 'Jameson');
const jamesonDistillery = brand('br-2', 'Jameson Distillery');

let onChange: Mock<(item: BrandResponse | null) => void>;
let onCreateNew: Mock<(name: string) => void>;

beforeEach(() => {
  vi.clearAllMocks();
  onChange = vi.fn();
  onCreateNew = vi.fn();
  vi.mocked(brandsApi.search).mockResolvedValue([jameson]);
  vi.mocked(brandsApi.suggest).mockResolvedValue([]);
});

function renderPicker(value = '') {
  renderWithQueries(<BrandPicker value={value} onChange={onChange} onCreateNew={onCreateNew} />);
}

const type = (text: string) => fireEvent.change(screen.getByLabelText('Brand'), { target: { value: text } });

describe('BrandPicker search', () => {
  it('debounces typing into a single search for the settled term', async () => {
    renderPicker();
    type('ja');
    type('jam');
    type('jame');
    expect(brandsApi.search).not.toHaveBeenCalled();
    expect(await screen.findByRole('button', { name: 'Jameson' })).toBeInTheDocument();
    expect(brandsApi.search).toHaveBeenCalledTimes(1);
    expect(brandsApi.search).toHaveBeenCalledWith('jame');
  });

  it('asks for two letters before searching', async () => {
    renderPicker();
    type('j');
    expect(await screen.findByText('Type at least 2 letters or digits')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /create new brand/i })).not.toBeInTheDocument();
    expect(brandsApi.search).not.toHaveBeenCalled();
  });

  it('reports the picked brand', async () => {
    renderPicker();
    type('jameson');
    fireEvent.click(await screen.findByRole('button', { name: 'Jameson' }));
    expect(onChange).toHaveBeenCalledWith(jameson);
  });

  it('offers "Create new" only once the results are in', async () => {
    renderPicker();
    expect(screen.queryByRole('button', { name: /create new brand/i })).not.toBeInTheDocument();
    type('Jamesen');
    expect(screen.queryByRole('button', { name: /create new brand/i })).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Create new brand "Jamesen"' })).toBeInTheDocument();
  });

  it('shows a brand chosen elsewhere by looking it up, and clears it on Change', async () => {
    vi.mocked(brandsApi.getById).mockResolvedValue(jameson);
    renderPicker('br-1');
    expect(await screen.findByText('Jameson')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Change brand' }));
    expect(onChange).toHaveBeenCalledWith(null);
  });
});

describe('BrandPicker "did you mean"', () => {
  async function chooseCreate(text: string) {
    type(text);
    fireEvent.click(await screen.findByRole('button', { name: `Create new brand "${text}"` }));
  }

  it('offers near matches before creating, and picking one selects it', async () => {
    vi.mocked(brandsApi.suggest).mockResolvedValue([jameson, jamesonDistillery]);
    renderPicker();
    await chooseCreate('Jamesen');
    expect(await screen.findByText('Did you mean...?')).toBeInTheDocument();
    expect(brandsApi.suggest).toHaveBeenCalledWith('Jamesen');
    fireEvent.click(screen.getByRole('button', { name: 'Jameson Distillery' }));
    expect(onChange).toHaveBeenCalledWith(jamesonDistillery);
    expect(onCreateNew).not.toHaveBeenCalled();
  });

  it('"No, create" creates the typed name', async () => {
    vi.mocked(brandsApi.suggest).mockResolvedValue([jameson]);
    renderPicker();
    await chooseCreate('Jamesen');
    fireEvent.click(await screen.findByRole('button', { name: "No, create 'Jamesen'" }));
    expect(onCreateNew).toHaveBeenCalledWith('Jamesen');
    expect(onChange).not.toHaveBeenCalled();
  });

  it('goes straight to creating when nothing is near', async () => {
    renderPicker();
    await chooseCreate('Brand New Name');
    await waitFor(() => expect(onCreateNew).toHaveBeenCalledWith('Brand New Name'));
    expect(onCreateNew).toHaveBeenCalledTimes(1);
  });

  it('never blocks creation when the suggestion lookup fails', async () => {
    vi.mocked(brandsApi.suggest).mockRejectedValue(new Error('network down'));
    renderPicker();
    await chooseCreate('Jamesen');
    await waitFor(() => expect(onCreateNew).toHaveBeenCalledWith('Jamesen'));
  });

  it('still offers "Create new" when the search itself fails', async () => {
    vi.mocked(brandsApi.search).mockRejectedValue(new Error('network down'));
    renderPicker();
    type('Jamesen');
    expect(await screen.findByRole('button', { name: 'Create new brand "Jamesen"' })).toBeInTheDocument();
  });
});
