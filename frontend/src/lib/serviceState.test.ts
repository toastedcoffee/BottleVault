// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  getServiceState,
  subscribeServiceState,
  markUnavailable,
  markAvailable,
} from './serviceState';

beforeEach(() => {
  markAvailable();
});

describe('serviceState', () => {
  it('starts available', () => {
    expect(getServiceState()).toEqual({ unavailable: false, retryAfterSeconds: null });
  });

  it('records the retry hint when marked unavailable', () => {
    markUnavailable(60);

    expect(getServiceState()).toEqual({ unavailable: true, retryAfterSeconds: 60 });
  });

  it('returns a stable snapshot reference while the state is unchanged', () => {
    // useSyncExternalStore re-renders forever if getSnapshot returns a fresh
    // object each call, so identity stability is a hard requirement, not a nicety.
    expect(getServiceState()).toBe(getServiceState());
  });

  it('notifies subscribers on a real transition only', () => {
    const listener = vi.fn();
    subscribeServiceState(listener);

    markUnavailable(60);
    markUnavailable(60); // same state — no second notification

    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('notifies when the retry hint changes while still unavailable', () => {
    markUnavailable(60);
    const listener = vi.fn();
    subscribeServiceState(listener);

    markUnavailable(900);

    expect(listener).toHaveBeenCalledTimes(1);
    expect(getServiceState().retryAfterSeconds).toBe(900);
  });

  it('stops notifying after unsubscribe', () => {
    const listener = vi.fn();
    const unsubscribe = subscribeServiceState(listener);
    unsubscribe();

    markUnavailable(60);

    expect(listener).not.toHaveBeenCalled();
  });

  it('clears back to available', () => {
    markUnavailable(60);

    markAvailable();

    expect(getServiceState()).toEqual({ unavailable: false, retryAfterSeconds: null });
  });
});
