// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import type { AlcoholType } from '../types/product';

/**
 * Pour math for fill-level tracking.
 *
 * Two input paths with deliberately different precision:
 * - the manual "Remaining" slider is coarse (10% steps) — eyeballing a bottle
 *   can't honestly distinguish 62% from 60%;
 * - computed pours stay exact and are rounded only at the end, so logged
 *   pours land on non-round values like 54%.
 */

export const OZ_TO_ML = 29.5735;

/** Parse a product size label like "750ml", "70 cl", or "1.75L" into milliliters. */
export function parseSizeToMl(size: string | null | undefined): number | null {
  if (!size) return null;
  const match = size.trim().match(/^(\d+(?:\.\d+)?)\s*(ml|cl|l)$/i);
  if (!match?.[1] || !match[2]) return null;
  const value = Number(match[1]);
  const unit = match[2].toLowerCase();
  const ml = unit === 'l' ? value * 1000 : unit === 'cl' ? value * 10 : value;
  return ml > 0 ? ml : null;
}

/**
 * Fill percentage after logging a pour of `pourOz` fluid ounces.
 * Always decrements by at least one point so a logged pour is never a
 * visual no-op (tiny pour on a huge bottle), and clamps at empty.
 */
export function percentAfterPour(currentPercent: number, sizeMl: number, pourOz: number): number {
  const pourPercent = ((pourOz * OZ_TO_ML) / sizeMl) * 100;
  const next = Math.round(currentPercent - pourPercent);
  return Math.max(0, Math.min(currentPercent - 1, next));
}

const BEER_TYPES: ReadonlySet<AlcoholType> = new Set([
  'BEER', 'IPA', 'STOUT', 'LAGER', 'PILSNER', 'WHEAT_BEER',
]);

/** Standard serving for the product type: 5 oz for wine, 12 oz for beer, 1.5 oz otherwise. */
export function defaultPourOz(type: AlcoholType): number {
  if (type.startsWith('WINE')) return 5;
  if (BEER_TYPES.has(type)) return 12;
  return 1.5;
}

export interface PourOption {
  oz: number;
  label: string;
}

export const POUR_OPTIONS: PourOption[] = [
  { oz: 0.5, label: '0.5 oz - taste' },
  { oz: 1, label: '1 oz' },
  { oz: 1.5, label: '1.5 oz - standard pour' },
  { oz: 2, label: '2 oz' },
  { oz: 3, label: '3 oz - double' },
  { oz: 5, label: '5 oz - wine glass' },
  { oz: 12, label: '12 oz - full beer' },
];
