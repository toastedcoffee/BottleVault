// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
/**
 * Whether the API is currently answering.
 *
 * Lives outside React because the thing that detects the outage is an axios
 * response interceptor, which has no component tree to write context into.
 * Components read it through useSyncExternalStore.
 */
export interface ServiceState {
  unavailable: boolean;
  retryAfterSeconds: number | null;
}

const AVAILABLE: ServiceState = { unavailable: false, retryAfterSeconds: null };

let state: ServiceState = AVAILABLE;
const listeners = new Set<() => void>();

function emit(next: ServiceState): void {
  state = next;
  listeners.forEach((listener) => listener());
}

/**
 * Referentially stable while the state is unchanged — useSyncExternalStore
 * compares snapshots by identity and loops forever on a fresh object per call.
 */
export function getServiceState(): ServiceState {
  return state;
}

export function subscribeServiceState(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function markUnavailable(retryAfterSeconds: number | null): void {
  if (state.unavailable && state.retryAfterSeconds === retryAfterSeconds) return;
  emit({ unavailable: true, retryAfterSeconds });
}

export function markAvailable(): void {
  if (!state.unavailable) return;
  emit(AVAILABLE);
}
