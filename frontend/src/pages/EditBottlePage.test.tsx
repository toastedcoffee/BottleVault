// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import EditBottlePage from './EditBottlePage';
import {
  useBottle,
  useUpdateBottle,
  useUploadBottleImage,
  useDeleteBottleImage,
} from '../hooks/useBottles';
import type { BottleResponse } from '../types/bottle';

vi.mock('../hooks/useBottles', () => ({
  useBottle: vi.fn(),
  useUpdateBottle: vi.fn(),
  useUploadBottleImage: vi.fn(),
  useDeleteBottleImage: vi.fn(),
}));

// BottleImage fetches the image bytes through the API client; irrelevant here.
vi.mock('../components/bottles/BottleImage', () => ({
  default: () => <div data-testid="bottle-image" />,
}));

function makeBottle(overrides: Partial<BottleResponse> = {}): BottleResponse {
  return {
    id: 'b1',
    status: 'OPENED',
    percentageLeft: 60,
    purchaseDate: '2026-01-15',
    purchaseLocation: 'Local Shop',
    purchaseCost: 49.99,
    notes: 'Original note',
    rating: 4,
    storageLocation: 'Cabinet A',
    imagePath: null,
    createdAt: '2026-01-15T00:00:00Z',
    updatedAt: '2026-01-15T00:00:00Z',
    product: {
      id: 'p1',
      name: 'Test Product',
      barcode: null,
      type: 'BOURBON',
      subtype: null,
      size: '750ml',
      abv: 45,
      description: null,
      imageUrl: null,
      isUserCreated: false,
      brand: { id: 'br1', name: 'Test Brand', country: null, website: null },
    },
    ...overrides,
  };
}

function mockBottleQuery(data: BottleResponse | undefined, isLoading = false) {
  vi.mocked(useBottle).mockReturnValue({ data, isLoading } as unknown as ReturnType<typeof useBottle>);
}

let updateMutateAsync: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  updateMutateAsync = vi.fn().mockResolvedValue(undefined);
  vi.mocked(useUpdateBottle).mockReturnValue({
    mutateAsync: updateMutateAsync,
    isPending: false,
  } as unknown as ReturnType<typeof useUpdateBottle>);
  vi.mocked(useUploadBottleImage).mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useUploadBottleImage>);
  vi.mocked(useDeleteBottleImage).mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useDeleteBottleImage>);
});

// A fresh element per (re)render — reusing one JSX object would let React
// bail out on the identical element and skip re-reading the mocked hooks.
const ui = () => (
  <MemoryRouter initialEntries={['/inventory/b1/edit']}>
    <Routes>
      <Route path="/inventory/:id/edit" element={<EditBottlePage />} />
      <Route path="/inventory" element={<div>Inventory list route</div>} />
    </Routes>
  </MemoryRouter>
);

function statusSelect(): HTMLSelectElement {
  // First combobox in the form; the second is the rating select.
  return screen.getAllByRole('combobox')[0] as HTMLSelectElement;
}

describe('form population', () => {
  it('shows a spinner while the bottle is loading', () => {
    mockBottleQuery(undefined, true);
    render(ui());

    expect(screen.queryByText('Edit Bottle')).not.toBeInTheDocument();
  });

  it('shows a not-found message when the bottle is missing', () => {
    mockBottleQuery(undefined, false);
    render(ui());

    expect(screen.getByText('Bottle not found')).toBeInTheDocument();
  });

  it('populates the form from the fetched bottle', () => {
    mockBottleQuery(makeBottle());
    render(ui());

    expect(screen.getByText('Test Brand - Test Product')).toBeInTheDocument();
    expect(statusSelect().value).toBe('OPENED');
    expect(screen.getByRole('slider')).toHaveValue('60');
    expect(screen.getByDisplayValue('Original note')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Local Shop')).toBeInTheDocument();
    expect(screen.getByDisplayValue('49.99')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2026-01-15')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Cabinet A')).toBeInTheDocument();
  });
});

describe('fill slider', () => {
  it('shows a precise pour-derived value exactly instead of snapping the thumb', () => {
    mockBottleQuery(makeBottle({ percentageLeft: 56 }));
    render(ui());

    // No `step` forcing the thumb to a 10% multiple - 56 rests at 56.
    expect(screen.getByRole('slider')).toHaveValue('56');
    expect(screen.getByText('Remaining (56%)')).toBeInTheDocument();
  });

  it('snaps a drag to the nearest 10% and submits the snapped value', async () => {
    mockBottleQuery(makeBottle({ percentageLeft: 56 }));
    render(ui());

    fireEvent.change(screen.getByRole('slider'), { target: { value: '57' } });

    expect(screen.getByRole('slider')).toHaveValue('60');
    expect(screen.getByText('Remaining (60%)')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Save Changes' }));
    expect(await screen.findByText('Inventory list route')).toBeInTheDocument();
    expect(updateMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ percentageLeft: 60 }) })
    );
  });

  it('preserves a precise value when the slider is left untouched', async () => {
    mockBottleQuery(makeBottle({ percentageLeft: 56 }));
    render(ui());

    fireEvent.click(screen.getByRole('button', { name: 'Save Changes' }));
    expect(await screen.findByText('Inventory list route')).toBeInTheDocument();
    expect(updateMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ percentageLeft: 56 }) })
    );
  });
});

describe('id guard', () => {
  it('does not clobber unsaved edits when the same bottle refetches in the background', () => {
    mockBottleQuery(makeBottle());
    const { rerender } = render(ui());

    const notes = screen.getByDisplayValue('Original note');
    fireEvent.change(notes, { target: { value: 'My unsaved edit' } });

    // Background refetch delivers a fresh object (new reference, same id).
    mockBottleQuery(makeBottle({ notes: 'Server-side change' }));
    rerender(ui());

    expect(screen.getByDisplayValue('My unsaved edit')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('Server-side change')).not.toBeInTheDocument();
  });

  it('repopulates the form when a different bottle id loads', () => {
    mockBottleQuery(makeBottle());
    const { rerender } = render(ui());

    fireEvent.change(screen.getByDisplayValue('Original note'), {
      target: { value: 'My unsaved edit' },
    });

    mockBottleQuery(
      makeBottle({
        id: 'b2',
        status: 'EMPTY',
        percentageLeft: 0,
        notes: 'Second bottle note',
      })
    );
    rerender(ui());

    expect(screen.getByDisplayValue('Second bottle note')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('My unsaved edit')).not.toBeInTheDocument();
    expect(statusSelect().value).toBe('EMPTY');
    expect(screen.getByRole('slider')).toHaveValue('0');
  });
});

describe('submit', () => {
  it('sends the edited values and navigates back to the inventory', async () => {
    mockBottleQuery(makeBottle());
    render(ui());

    fireEvent.change(screen.getByDisplayValue('Original note'), {
      target: { value: 'Edited note' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

    expect(await screen.findByText('Inventory list route')).toBeInTheDocument();
    expect(updateMutateAsync).toHaveBeenCalledWith({
      id: 'b1',
      data: {
        status: 'OPENED',
        percentageLeft: 60,
        purchaseDate: '2026-01-15',
        purchaseLocation: 'Local Shop',
        purchaseCost: 49.99,
        notes: 'Edited note',
        rating: 4,
        storageLocation: 'Cabinet A',
      },
    });
  });

  it('maps cleared optional fields to undefined instead of empty strings', async () => {
    mockBottleQuery(
      makeBottle({
        purchaseDate: null,
        purchaseLocation: null,
        purchaseCost: null,
        notes: null,
        rating: null,
        storageLocation: null,
      })
    );
    render(ui());

    fireEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

    expect(await screen.findByText('Inventory list route')).toBeInTheDocument();
    expect(updateMutateAsync).toHaveBeenCalledWith({
      id: 'b1',
      data: {
        status: 'OPENED',
        percentageLeft: 60,
        purchaseDate: undefined,
        purchaseLocation: undefined,
        purchaseCost: undefined,
        notes: undefined,
        rating: undefined,
        storageLocation: undefined,
      },
    });
  });

  it('shows an error and stays on the page when the update fails', async () => {
    mockBottleQuery(makeBottle());
    updateMutateAsync.mockRejectedValue(new Error('boom'));
    render(ui());

    fireEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

    expect(await screen.findByText('Failed to update bottle')).toBeInTheDocument();
    expect(screen.queryByText('Inventory list route')).not.toBeInTheDocument();
  });
});
