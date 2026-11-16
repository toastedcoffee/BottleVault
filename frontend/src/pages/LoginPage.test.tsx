// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import LoginPage from './LoginPage';
import { useAuth } from '../context/useAuth';
import { useAuthConfig } from '../hooks/useAuthConfig';
import type { AuthConfig } from '../types/auth';

vi.mock('../context/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('../hooks/useAuthConfig', () => ({ useAuthConfig: vi.fn() }));

const login = vi.fn();
const register = vi.fn();

function withConfig(config: Partial<AuthConfig> = {}) {
  vi.mocked(useAuthConfig).mockReturnValue({
    data: { registrationMode: 'open', turnstileSiteKey: null, privacyUrl: null, ...config },
  } as unknown as ReturnType<typeof useAuthConfig>);
}

beforeEach(() => {
  login.mockReset().mockResolvedValue(undefined);
  register.mockReset().mockResolvedValue(undefined);
  vi.mocked(useAuth).mockReturnValue({ login, register } as unknown as ReturnType<typeof useAuth>);
  withConfig();
});

afterEach(() => {
  window.history.replaceState(null, '', '/');
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

describe('registration modes', () => {
  it('hides the register option when registration is closed', () => {
    withConfig({ registrationMode: 'closed' });
    render(<LoginPage />);
    expect(screen.queryByRole('button', { name: /register/i })).not.toBeInTheDocument();
  });

  it('does not ask for an invite code when registration is open', () => {
    render(<LoginPage initialMode="register" />);
    expect(screen.queryByLabelText('Invite code')).not.toBeInTheDocument();
  });

  it('asks for an invite code in invite mode and sends it', async () => {
    withConfig({ registrationMode: 'invite' });
    render(<LoginPage initialMode="register" />);
    fireEvent.change(screen.getByLabelText('Invite code'), { target: { value: '7K3M-9Q2X-H4TR-8WPA' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create Account' }));
    await waitFor(() => expect(register).toHaveBeenCalledWith(
      expect.objectContaining({ email: 'user@example.com', inviteCode: '7K3M-9Q2X-H4TR-8WPA' }),
    ));
  });

  it('opens registration from an invite link, pre-fills the code, and clears it from the address bar', () => {
    withConfig({ registrationMode: 'invite' });
    window.history.replaceState(null, '', '/register#invite=7K3M-9Q2X-H4TR-8WPA');
    render(<LoginPage />);
    expect(screen.getByLabelText('Invite code')).toHaveValue('7K3M-9Q2X-H4TR-8WPA');
    expect(screen.getByRole('heading', { name: 'Create Account' })).toBeInTheDocument();
    expect(window.location.hash).toBe('');
  });

  it('links the privacy notice only when it is a real web address', () => {
    withConfig({ privacyUrl: 'https://example.com/privacy' });
    const { unmount } = render(<LoginPage initialMode="register" />);
    expect(screen.getByRole('link', { name: 'Privacy notice' })).toHaveAttribute('href', 'https://example.com/privacy');
    unmount();
    withConfig({ privacyUrl: 'javascript:alert(1)' });
    render(<LoginPage initialMode="register" />);
    expect(screen.queryByRole('link', { name: 'Privacy notice' })).not.toBeInTheDocument();
  });

  it('shows the retry time when sign-in is throttled', async () => {
    const { AxiosError } = await import('axios');
    const response = { status: 429, data: {}, headers: { 'retry-after': '600' }, statusText: '', config: { headers: {} } };
    login.mockRejectedValue(new AxiosError('429', 'ERR_BAD_REQUEST', undefined, null, response as never));
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign In' }));
    expect(await screen.findByText('Too many attempts. Try again in 10 minutes.')).toBeInTheDocument();
  });
});
