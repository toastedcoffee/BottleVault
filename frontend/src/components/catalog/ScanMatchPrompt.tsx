// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useState } from 'react';
import { useBrandSearch, useBrandSuggestions } from '../../hooks/useBrands';
import { useProductSuggestions } from '../../hooks/useProducts';
import type { BrandResponse } from '../../types/brand';
import type { ProductResponse } from '../../types/product';
import DidYouMean from './DidYouMean';
import { describeProduct } from './describeProduct';

interface ScanMatchPromptProps {
  /** The brand name a barcode provider supplied, if any. */
  brandName: string | null;
  /** The product name a barcode provider supplied. */
  productName: string;
  /** The existing brand the form is on, or null while the brand is still a new name. */
  brandId: string | null;
  onUseBrand: (brand: BrandResponse) => void;
  onUseProduct: (product: ProductResponse) => void;
}

const SUGGEST_MAX = 5;
const SEARCH_HITS = 3;

/**
 * "Did you mean" for names a barcode scan filled in. The user never typed these,
 * so nothing has checked them against the catalog yet. The brand step therefore
 * offers the top search hits (an exact name among them) as well as near matches;
 * once the form is on an existing brand, the product step offers near product
 * names within it. Either step steps aside when it has nothing to offer.
 */
export default function ScanMatchPrompt({ brandName, productName, brandId, onUseBrand, onUseProduct }: ScanMatchPromptProps) {
  const [brandDone, setBrandDone] = useState(!brandName || brandId !== null);
  const [productDone, setProductDone] = useState(false);

  const brandOpen = !brandDone && brandName !== null;
  const brandHits = useBrandSearch(brandOpen ? brandName : '');
  const brandNear = useBrandSuggestions(brandOpen ? brandName : null);

  const productOpen = brandDone && !productDone && brandId !== null && productName.trim() !== '';
  const productNear = useProductSuggestions(productOpen ? brandId : null, productOpen ? productName : null);

  if (brandOpen) {
    const seen = new Set<string>();
    const candidates = [...(brandHits.data ?? []).slice(0, SEARCH_HITS), ...(brandNear.data ?? [])]
      .filter((b) => {
        if (seen.has(b.id)) return false;
        seen.add(b.id);
        return true;
      })
      .slice(0, SUGGEST_MAX);
    return (
      <DidYouMean<BrandResponse>
        key="brand"
        intro={`The scan names the brand "${brandName}".`}
        typed={brandName}
        loading={brandHits.isLoading || brandNear.isLoading}
        suggestions={candidates}
        describe={(b) => b.displayName}
        keyOf={(b) => b.id}
        onPick={(b) => { setBrandDone(true); onUseBrand(b); }}
        onCreate={() => setBrandDone(true)}
      />
    );
  }

  if (productOpen) {
    return (
      <DidYouMean<ProductResponse>
        key="product"
        intro={`The scan names the product "${productName}".`}
        typed={productName}
        loading={productNear.isLoading}
        suggestions={productNear.data ?? []}
        describe={describeProduct}
        keyOf={(p) => p.id}
        onPick={(p) => { setProductDone(true); onUseProduct(p); }}
        onCreate={() => setProductDone(true)}
      />
    );
  }

  return null;
}
