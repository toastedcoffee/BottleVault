// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import AppLayout from './AppLayout';
import { useAuth } from '../../context/useAuth';
import type { UserProfile } from '../../types/auth';

vi.mock('../../context/useAuth', () => ({
  useAuth: vi.fn(),
}));

const profile: UserProfile = {
  id: 'u1',
  email: 'user@example.com',
  displayName: 'Test User',
  defaultCurrency: 'USD',
  measurementUnit: 'ML',
};

beforeEach(() => {
  vi.mocked(useAuth).mockReturnValue({
    user: profile,
    logout: vi.fn(),
  } as unknown as ReturnType<typeof useAuth>);
});

function ui() {
  return (
    <MemoryRouter initialEntries={['/inventory']}>
      <Routes>
        <Route path="/inventory" element={<AppLayout />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('source offer mount', () => {
  it('renders the AGPL source offer in the app shell footer', () => {
    render(ui());

    expect(screen.getByRole('link', { name: 'Source' })).toBeInTheDocument();
    expect(screen.getByText(/AGPL-3\.0-only/)).toBeInTheDocument();
  });

  it('carries the trademark on the app shell wordmark', () => {
    render(ui());

    expect(screen.getByRole('link', { name: `BottleVault${'™'}` })).toBeInTheDocument();
  });
});
