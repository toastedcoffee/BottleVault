// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, act } from '@testing-library/react';
import TurnstileWidget from './TurnstileWidget';

type Options = { callback: (t: string) => void; 'expired-callback': () => void; sitekey: string; action: string };
let options: Options | null = null;

beforeEach(() => {
  options = null;
  window.turnstile = {
    render: vi.fn((_el: HTMLElement, o: Record<string, unknown>) => { options = o as unknown as Options; return 'widget-1'; }),
    reset: vi.fn(),
    remove: vi.fn(),
  };
});

afterEach(() => {
  delete window.turnstile;
});

describe('TurnstileWidget', () => {
  it('renders with the site key and action, and reports tokens', async () => {
    const onToken = vi.fn();
    await act(async () => { render(<TurnstileWidget siteKey="1x00000000000000000000AA" action="login" onToken={onToken} resetKey={0} />); });
    expect(options?.sitekey).toBe('1x00000000000000000000AA');
    expect(options?.action).toBe('login');
    act(() => options!.callback('token-1'));
    expect(onToken).toHaveBeenCalledWith('token-1');
    act(() => options!['expired-callback']());
    expect(onToken).toHaveBeenLastCalledWith(null);
  });

  it('asks Turnstile for a fresh token when resetKey changes', async () => {
    const { rerender } = await act(async () =>
      render(<TurnstileWidget siteKey="k" action="login" onToken={vi.fn()} resetKey={0} />));
    rerender(<TurnstileWidget siteKey="k" action="login" onToken={vi.fn()} resetKey={1} />);
    expect(window.turnstile!.reset).toHaveBeenCalledWith('widget-1');
  });

  it('removes the widget when it unmounts', async () => {
    const { unmount } = await act(async () =>
      render(<TurnstileWidget siteKey="k" action="register" onToken={vi.fn()} resetKey={0} />));
    unmount();
    expect(window.turnstile!.remove).toHaveBeenCalledWith('widget-1');
  });
});
