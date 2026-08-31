// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
export interface BrandResponse {
  id: string;
  displayName: string;
  country: string | null;
  website: string | null;
}

export interface BrandCreateRequest {
  name: string;
  country?: string;
  website?: string;
}
