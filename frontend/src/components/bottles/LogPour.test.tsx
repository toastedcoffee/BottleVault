import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import LogPour from './LogPour';
import { useUpdateBottle } from '../../hooks/useBottles';
import type { BottleResponse } from '../../types/bottle';

vi.mock('../../hooks/useBottles', () => ({
  useUpdateBottle: vi.fn(),
}));

function makeBottle(overrides: Partial<BottleResponse> = {}): BottleResponse {
  return {
    id: 'b1',
    status: 'OPENED',
    percentageLeft: 60,
    purchaseDate: null,
    purchaseLocation: null,
    purchaseCost: null,
    notes: null,
    rating: null,
    storageLocation: null,
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

let mutate: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  mutate = vi.fn();
  vi.mocked(useUpdateBottle).mockReturnValue({
    mutate,
    isPending: false,
    isError: false,
  } as unknown as ReturnType<typeof useUpdateBottle>);
});

describe('logging pours', () => {
  it('logs a standard 1.5 oz pour for a spirit and previews the result', () => {
    render(<LogPour bottle={makeBottle()} />);

    // 1.5 oz of 750ml = 5.91% -> 60 - 5.91 -> 54
    expect(screen.getByText('Leaves 54%')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /log pour/i }));

    expect(mutate).toHaveBeenCalledWith({ id: 'b1', data: { percentageLeft: 54 } });
  });

  it('defaults wine to a 5 oz glass', () => {
    render(
      <LogPour bottle={makeBottle({ product: { ...makeBottle().product, type: 'WINE_RED' } })} />
    );

    expect((screen.getByLabelText('Pour size') as HTMLSelectElement).value).toBe('5');
    // 5 oz of 750ml = 19.72% -> 60 - 19.72 -> 40
    expect(screen.getByText('Leaves 40%')).toBeInTheDocument();
  });

  it('recomputes when a different pour size is selected', () => {
    render(<LogPour bottle={makeBottle()} />);

    fireEvent.change(screen.getByLabelText('Pour size'), { target: { value: '3' } });

    // 3 oz of 750ml = 11.83% -> 60 - 11.83 -> 48
    expect(screen.getByText('Leaves 48%')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /log pour/i }));
    expect(mutate).toHaveBeenCalledWith({ id: 'b1', data: { percentageLeft: 48 } });
  });

  it('opens an unopened bottle on its first pour', () => {
    render(<LogPour bottle={makeBottle({ status: 'UNOPENED', percentageLeft: 100 })} />);

    fireEvent.click(screen.getByRole('button', { name: /log pour/i }));

    expect(mutate).toHaveBeenCalledWith({
      id: 'b1',
      data: { percentageLeft: 94, status: 'OPENED' },
    });
  });

  it('marks the bottle empty when the pour finishes it', () => {
    render(<LogPour bottle={makeBottle({ percentageLeft: 5 })} />);

    expect(screen.getByText('Finishes the bottle')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /log pour/i }));

    expect(mutate).toHaveBeenCalledWith({
      id: 'b1',
      data: { percentageLeft: 0, status: 'EMPTY' },
    });
  });
});

describe('guard states', () => {
  it('shows an empty message instead of controls for an empty bottle', () => {
    render(<LogPour bottle={makeBottle({ status: 'EMPTY', percentageLeft: 0 })} />);

    expect(screen.getByText(/this bottle is empty/i)).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('asks for a bottle size when the product size is missing or unparseable', () => {
    render(
      <LogPour bottle={makeBottle({ product: { ...makeBottle().product, size: null } })} />
    );

    expect(screen.getByText(/add a bottle size/i)).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('shows an error message when the update fails', () => {
    vi.mocked(useUpdateBottle).mockReturnValue({
      mutate,
      isPending: false,
      isError: true,
    } as unknown as ReturnType<typeof useUpdateBottle>);

    render(<LogPour bottle={makeBottle()} />);

    expect(screen.getByText(/failed to log pour/i)).toBeInTheDocument();
  });
});
