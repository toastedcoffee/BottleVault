// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useCallback, useState } from 'react';
import { useProduct, useProductSearch, useProductSuggestions } from '../../hooks/useProducts';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import type { ProductResponse } from '../../types/product';
import SearchPicker from './SearchPicker';
import DidYouMean from './DidYouMean';
import { describeProduct } from './describeProduct';

interface ProductPickerProps {
  id?: string;
  /** The brand whose products are listed. */
  brandId: string;
  /** The selected product's id, or '' for none. */
  value: string;
  onChange: (product: ProductResponse | null) => void;
  /** The user chose to create a product with this name, after "did you mean". */
  onCreateNew: (name: string) => void;
}

const keyOf = (p: ProductResponse) => p.id;

/**
 * Search-as-you-type picker for one brand's products. With no term it lists the
 * brand's first 20; typing narrows them. "Create new" appears once a name is typed.
 */
export default function ProductPicker({ id = 'product-picker', brandId, value, onChange, onCreateNew }: ProductPickerProps) {
  const [term, setTerm] = useState('');
  const debounced = useDebouncedValue(term);
  const settled = debounced === term;
  const [picked, setPicked] = useState<ProductResponse | null>(null);
  const [pendingCreate, setPendingCreate] = useState<string | null>(null);

  const known = picked && picked.id === value ? picked : null;
  const byId = useProduct(value && !known ? value : '');
  const selected = known ?? (byId.data && byId.data.id === value ? byId.data : null);

  const search = useProductSearch(brandId, debounced);
  const suggestions = useProductSuggestions(pendingCreate === null ? null : brandId, pendingCreate);

  const pick = (product: ProductResponse) => {
    setPicked(product);
    setPendingCreate(null);
    setTerm('');
    onChange(product);
  };

  const create = useCallback(() => {
    if (pendingCreate === null) return;
    setPendingCreate(null);
    setTerm('');
    onCreateNew(pendingCreate);
  }, [pendingCreate, onCreateNew]);

  const typed = debounced.trim();
  const resolved = settled && (search.isSuccess || search.isError);

  return (
    <SearchPicker<ProductResponse>
      inputId={id}
      label="Product"
      placeholder="Search this brand's products..."
      term={term}
      onTermChange={setTerm}
      selected={value ? selected : null}
      selectedLoading={value !== '' && !selected && byId.isLoading}
      onClearSelection={() => { setPicked(null); onChange(null); }}
      describe={describeProduct}
      keyOf={keyOf}
      onPick={pick}
      results={settled ? search.data : undefined}
      searching={!settled || search.isFetching}
      searchFailed={settled && search.isError}
      hint={null}
      createLabel={resolved && typed !== '' ? `Create new product "${typed}"` : null}
      onCreateClick={() => setPendingCreate(typed)}
      didYouMean={
        pendingCreate !== null ? (
          <DidYouMean<ProductResponse>
            typed={pendingCreate}
            loading={suggestions.isLoading}
            suggestions={suggestions.data ?? []}
            describe={describeProduct}
            keyOf={keyOf}
            onPick={pick}
            onCreate={create}
          />
        ) : null
      }
    />
  );
}
