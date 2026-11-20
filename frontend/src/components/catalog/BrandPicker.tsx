// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useCallback, useState } from 'react';
import { useBrand, useBrandSearch, useBrandSuggestions } from '../../hooks/useBrands';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { isSearchable } from '../../lib/catalogSearch';
import type { BrandResponse } from '../../types/brand';
import SearchPicker from './SearchPicker';
import DidYouMean from './DidYouMean';

interface BrandPickerProps {
  id?: string;
  /** The selected brand's id, or '' for none. */
  value: string;
  onChange: (brand: BrandResponse | null) => void;
  /** The user chose to create a brand with this name, after "did you mean". */
  onCreateNew: (name: string) => void;
}

const describe = (b: BrandResponse) => b.displayName;
const keyOf = (b: BrandResponse) => b.id;

/** Search-as-you-type brand picker: debounced search, "Create new" beside the results, then "did you mean". */
export default function BrandPicker({ id = 'brand-picker', value, onChange, onCreateNew }: BrandPickerProps) {
  const [term, setTerm] = useState('');
  const debounced = useDebouncedValue(term);
  const settled = debounced === term;
  const [picked, setPicked] = useState<BrandResponse | null>(null);
  const [pendingCreate, setPendingCreate] = useState<string | null>(null);

  // A selection the picker did not make (a scan, or a "did you mean" elsewhere) is looked up by id.
  const known = picked && picked.id === value ? picked : null;
  const byId = useBrand(value && !known ? value : '');
  const selected = known ?? (byId.data && byId.data.id === value ? byId.data : null);

  const search = useBrandSearch(debounced);
  const suggestions = useBrandSuggestions(pendingCreate);
  const typed = debounced.trim();
  const searchable = isSearchable(typed);

  const pick = (brand: BrandResponse) => {
    setPicked(brand);
    setPendingCreate(null);
    setTerm('');
    onChange(brand);
  };

  const create = useCallback(() => {
    if (pendingCreate === null) return;
    setPendingCreate(null);
    setTerm('');
    onCreateNew(pendingCreate);
  }, [pendingCreate, onCreateNew]);

  const resolved = settled && searchable && (search.isSuccess || search.isError);

  return (
    <SearchPicker<BrandResponse>
      inputId={id}
      label="Brand"
      placeholder="Search brands..."
      term={term}
      onTermChange={setTerm}
      selected={value ? selected : null}
      selectedLoading={value !== '' && !selected && byId.isLoading}
      onClearSelection={() => { setPicked(null); onChange(null); }}
      describe={describe}
      keyOf={keyOf}
      onPick={pick}
      results={settled && searchable ? search.data : undefined}
      searching={term.trim() !== '' && isSearchable(term.trim()) && (!settled || search.isFetching)}
      searchFailed={settled && searchable && search.isError}
      hint={term.trim() !== '' && !isSearchable(term.trim()) ? 'Type at least 2 letters or digits' : null}
      createLabel={resolved ? `Create new brand "${typed}"` : null}
      onCreateClick={() => setPendingCreate(typed)}
      didYouMean={
        pendingCreate !== null ? (
          <DidYouMean<BrandResponse>
            typed={pendingCreate}
            loading={suggestions.isLoading}
            suggestions={suggestions.data ?? []}
            describe={describe}
            keyOf={keyOf}
            onPick={pick}
            onCreate={create}
          />
        ) : null
      }
    />
  );
}
