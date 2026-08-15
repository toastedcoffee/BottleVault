// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import SourceOffer from './SourceOffer';

const REPO = 'https://github.com/toastedcoffee/BottleVault';

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('SourceOffer', () => {
  it('links to the exact commit when a SHA was baked in at build time', () => {
    vi.stubEnv('VITE_GIT_SHA', '1234567890abcdef');

    render(<SourceOffer />);

    expect(screen.getByRole('link', { name: 'Source' })).toHaveAttribute(
      'href',
      `${REPO}/tree/1234567890abcdef`,
    );
  });

  it('shows the short SHA so a user can identify their running instance', () => {
    vi.stubEnv('VITE_GIT_SHA', '1234567890abcdef');

    render(<SourceOffer />);

    expect(screen.getByText('1234567')).toBeInTheDocument();
  });

  it('falls back to the repo root when no SHA was injected', () => {
    vi.stubEnv('VITE_GIT_SHA', '');

    render(<SourceOffer />);

    expect(screen.getByRole('link', { name: 'Source' })).toHaveAttribute('href', REPO);
  });

  it('never renders the string undefined', () => {
    vi.stubEnv('VITE_GIT_SHA', '');

    const { container } = render(<SourceOffer />);

    expect(container.textContent).not.toContain('undefined');
  });

  it('states the licence and carries no trademark mark of its own', () => {
    const { container } = render(<SourceOffer />);

    expect(container.textContent).not.toContain('\u2122');
    expect(container.textContent).toContain('AGPL-3.0-only');
    expect(container.textContent).not.toContain('-or-later');
  });

  it('opens the source link safely in a new tab', () => {
    render(<SourceOffer />);

    const link = screen.getByRole('link', { name: 'Source' });
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });
});
