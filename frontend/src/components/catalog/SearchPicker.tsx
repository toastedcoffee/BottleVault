// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import type { ReactNode } from 'react';
import { Plus } from 'lucide-react';

export const PICKER_INPUT_CLASS =
  'w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright';

interface SearchPickerProps<T> {
  inputId: string;
  label: string;
  placeholder: string;
  term: string;
  onTermChange: (term: string) => void;
  /** The current selection, or null to show the search box. */
  selected: T | null;
  /** True while a selection made elsewhere (e.g. a scan) is being looked up by id. */
  selectedLoading: boolean;
  onClearSelection: () => void;
  describe: (item: T) => string;
  keyOf: (item: T) => string;
  onPick: (item: T) => void;
  /** Results for the settled term, or undefined while there is nothing to show yet. */
  results: T[] | undefined;
  searching: boolean;
  searchFailed: boolean;
  /** A line shown instead of results, e.g. "Type at least 2 letters". */
  hint: string | null;
  /** The "Create new" button's label, or null when there is nothing to create yet. */
  createLabel: string | null;
  onCreateClick: () => void;
  /** The "did you mean" step, which replaces the results while it is open. */
  didYouMean: ReactNode;
}

/**
 * A search-as-you-type picker for one kind of catalog row. Presentational: the
 * brand and product pickers own the queries and the debounce. "Create new" sits
 * only beside the results, so a user always sees what already exists first.
 */
export default function SearchPicker<T>(props: SearchPickerProps<T>) {
  const {
    inputId, label, placeholder, term, onTermChange, selected, selectedLoading, onClearSelection,
    describe, keyOf, onPick, results, searching, searchFailed, hint, createLabel, onCreateClick, didYouMean,
  } = props;

  if (selected || selectedLoading) {
    return (
      <div>
        <span className="block text-sm font-medium text-text-mid mb-1">{label}</span>
        <div className="flex items-center justify-between gap-2 px-3 py-2 bg-surface border border-border rounded-md">
          <span className="text-sm text-text-hi">{selected ? describe(selected) : 'Loading...'}</span>
          <button
            type="button"
            onClick={onClearSelection}
            aria-label={`Change ${label.toLowerCase()}`}
            className="text-sm text-primary-bright hover:text-primary"
          >
            Change
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <label htmlFor={inputId} className="block text-sm font-medium text-text-mid mb-1">{label}</label>
      <input
        id={inputId}
        type="search"
        autoComplete="off"
        value={term}
        onChange={(e) => onTermChange(e.target.value)}
        placeholder={placeholder}
        className={PICKER_INPUT_CLASS}
      />
      {didYouMean ?? (
        <div aria-live="polite">
          {hint && <p className="text-xs text-text-mid mt-1">{hint}</p>}
          {searching && !results && <p className="text-xs text-text-mid mt-1">Searching...</p>}
          {searchFailed && <p className="text-xs text-text-mid mt-1">The catalog search did not answer. You can still create a new entry.</p>}
          {results && results.length > 0 && (
            <ul aria-label={`${label} results`} className="mt-1 max-h-64 overflow-y-auto border border-border rounded-md divide-y divide-border">
              {results.map((item) => (
                <li key={keyOf(item)}>
                  <button
                    type="button"
                    onClick={() => onPick(item)}
                    className="w-full text-left px-3 py-2 text-sm text-text-hi hover:bg-surface-2"
                  >
                    {describe(item)}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {results && results.length === 0 && <p className="text-xs text-text-mid mt-1">No matches.</p>}
          {createLabel && (
            <button
              type="button"
              onClick={onCreateClick}
              className="flex items-center gap-1 mt-1 text-sm text-primary-bright hover:text-primary"
            >
              <Plus className="w-3 h-3" />
              {createLabel}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
