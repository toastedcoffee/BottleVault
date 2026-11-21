// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import BottleFilters from './BottleFilters';

function renderFilters(onTypeChange = vi.fn()) {
  render(
    <BottleFilters
      search=""
      status=""
      type=""
      onSearchChange={vi.fn()}
      onStatusChange={vi.fn()}
      onTypeChange={onTypeChange}
    />
  );
  return onTypeChange;
}

describe('BottleFilters type filter', () => {
  it('offers Wine, Cider and Mead', () => {
    renderFilters();
    expect(screen.getByRole('option', { name: 'WINE' })).toHaveValue('WINE');
    expect(screen.getByRole('option', { name: 'CIDER' })).toHaveValue('CIDER');
    expect(screen.getByRole('option', { name: 'MEAD' })).toHaveValue('MEAD');
  });

  it('reports the chosen type', () => {
    const onTypeChange = renderFilters();
    fireEvent.change(screen.getByRole('option', { name: 'MEAD' }).closest('select')!, {
      target: { value: 'MEAD' },
    });
    expect(onTypeChange).toHaveBeenCalledWith('MEAD');
  });
});
