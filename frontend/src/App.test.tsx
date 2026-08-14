// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import App from './App';
import type { UserProfile } from './types/auth';

// Keep pages from hitting the network: every query stays pending, which is
// enough to tell which route rendered without stubbing per-endpoint data.
vi.mock('./api/client', () => ({
  default: {
    get: vi.fn(() => new Promise(() => {})),
    post: vi.fn(() => new Promise(() => {})),
    put: vi.fn(() => new Promise(() => {})),
    patch: vi.fn(() => new Promise(() => {})),
    delete: vi.fn(() => new Promise(() => {})),
  },
}));

const profile: UserProfile = {
  id: 'u1',
  email: 'user@example.com',
  displayName: 'Test User',
  defaultCurrency: 'USD',
  measurementUnit: 'ML',
};

function seedAuthenticatedSession() {
  sessionStorage.setItem('accessToken', 'access-1');
  sessionStorage.setItem('refreshToken', 'refresh-1');
  sessionStorage.setItem('user', JSON.stringify(profile));
}

// App uses BrowserRouter, so the route under test is set through the real URL.
function renderAppAt(path: string) {
  window.history.pushState({}, '', path);
  return render(<App />);
}

beforeEach(() => {
  window.history.pushState({}, '', '/');
});

describe('route guards', () => {
  it('redirects an unauthenticated visitor from a protected route to /login', async () => {
    renderAppAt('/inventory');

    expect(await screen.findByRole('heading', { name: 'Sign In' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'My Collection' })).not.toBeInTheDocument();
  });

  it('redirects an unauthenticated visitor from an unknown path to /login', async () => {
    renderAppAt('/no-such-page');

    expect(await screen.findByRole('heading', { name: 'Sign In' })).toBeInTheDocument();
  });

  it('shows the app to an authenticated user on a protected route', async () => {
    seedAuthenticatedSession();

    renderAppAt('/inventory');

    expect(await screen.findByRole('heading', { name: 'My Collection' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Sign In' })).not.toBeInTheDocument();
  });

  it('redirects an authenticated user away from /login into the app', async () => {
    seedAuthenticatedSession();

    renderAppAt('/login');

    expect(await screen.findByRole('heading', { name: 'My Collection' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Sign In' })).not.toBeInTheDocument();
  });
});
