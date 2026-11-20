// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import type { ProductResponse } from '../../types/product';

/** A product as the pickers list it: its name, and its size when known. */
export const describeProduct = (p: ProductResponse) => (p.size ? `${p.displayName} (${p.size})` : p.displayName);
