// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import LoginPage from './LoginPage';
import { useAuth } from '../context/useAuth';

vi.mock('../context/useAuth', () => ({
  useAuth: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(useAuth).mockReturnValue({
    login: vi.fn(),
    register: vi.fn(),
  } as unknown as ReturnType<typeof useAuth>);
});

describe('source offer mount', () => {
  it('renders the AGPL source offer on the login page', () => {
    render(<LoginPage />);

    expect(screen.getByRole('link', { name: 'Source' })).toBeInTheDocument();
    expect(screen.getByText(/AGPL-3\.0-only/)).toBeInTheDocument();
  });

  it('carries the trademark on the login page wordmark', () => {
    render(<LoginPage />);

    expect(screen.getByRole('heading', { name: `BottleVault${'™'}` })).toBeInTheDocument();
  });
});
