// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useEffect, useRef } from 'react';

interface DidYouMeanProps<T> {
  /** What the user typed (or a scan supplied), shown back in the "No, create" button. */
  typed: string;
  /** True while the suggestions are still loading. */
  loading: boolean;
  /** Near matches. Empty, or a failed lookup, means there is nothing to ask. */
  suggestions: T[];
  describe: (item: T) => string;
  keyOf: (item: T) => string;
  onPick: (item: T) => void;
  onCreate: () => void;
  /** An optional first line, e.g. where the name came from. */
  intro?: string;
}

/**
 * The "Did you mean …?" step between choosing to create a catalog entry and
 * creating it. It is a prompt, never a gate: with no near match it steps aside
 * at once, and "No, create" is always one click away.
 */
export default function DidYouMean<T>({
  typed, loading, suggestions, describe, keyOf, onPick, onCreate, intro,
}: DidYouMeanProps<T>) {
  // Stepping aside calls onCreate exactly once, however often the parent re-renders.
  const steppedAside = useRef(false);
  const nothingToAsk = !loading && suggestions.length === 0;
  useEffect(() => {
    if (nothingToAsk && !steppedAside.current) {
      steppedAside.current = true;
      onCreate();
    }
  }, [nothingToAsk, onCreate]);

  if (loading) {
    return <p role="status" className="text-sm text-text-mid mt-2">Checking for similar names...</p>;
  }
  if (nothingToAsk) return null;

  return (
    <div role="group" aria-label="Did you mean" className="mt-2 p-3 rounded-md border border-primary/30 bg-primary/10">
      {intro && <p className="text-xs text-text-mid mb-1">{intro}</p>}
      <p className="text-sm font-medium text-text-hi mb-2">Did you mean...?</p>
      <ul className="space-y-1 mb-2">
        {suggestions.map((item) => (
          <li key={keyOf(item)}>
            <button
              type="button"
              onClick={() => onPick(item)}
              className="w-full text-left px-3 py-1.5 rounded-md text-sm text-text-hi bg-surface border border-border hover:bg-surface-2"
            >
              {describe(item)}
            </button>
          </li>
        ))}
      </ul>
      <button type="button" onClick={onCreate} className="text-sm text-primary-bright hover:text-primary">
        No, create '{typed}'
      </button>
    </div>
  );
}
