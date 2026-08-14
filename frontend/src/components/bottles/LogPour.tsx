// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useState } from 'react';
import { Wine } from 'lucide-react';
import { useUpdateBottle } from '../../hooks/useBottles';
import type { BottleResponse, BottleUpdateRequest } from '../../types/bottle';
import { POUR_OPTIONS, defaultPourOz, parseSizeToMl, percentAfterPour } from '../../lib/pour';
import InlineError from '../common/InlineError';

interface LogPourProps {
  bottle: BottleResponse;
}

/**
 * Logs a pour against a bottle: converts a serving size into a computed fill
 * percentage and saves it via the regular bottle update endpoint. Computed
 * values stay exact (not snapped to 10%) - only the manual slider on the
 * edit page is coarse. Logging a pour on an unopened bottle opens it;
 * pouring the last of a bottle marks it empty.
 */
export default function LogPour({ bottle }: LogPourProps) {
  const updateMutation = useUpdateBottle();
  const [pourOz, setPourOz] = useState(() => defaultPourOz(bottle.product.type));

  const sizeMl = parseSizeToMl(bottle.product.size);

  if (bottle.percentageLeft === 0) {
    return (
      <div className="mt-6 bg-surface-2 border border-border rounded-lg p-4 text-sm text-text-mid">
        This bottle is empty - no pours left to log.
      </div>
    );
  }

  if (sizeMl === null) {
    return (
      <div className="mt-6 bg-surface-2 border border-border rounded-lg p-4 text-sm text-text-mid">
        Add a bottle size (like 750ml) to the product to log pours.
      </div>
    );
  }

  const next = percentAfterPour(bottle.percentageLeft, sizeMl, pourOz);

  const logPour = () => {
    const data: BottleUpdateRequest = { percentageLeft: next };
    if (next === 0) {
      data.status = 'EMPTY';
    } else if (bottle.status === 'UNOPENED') {
      data.status = 'OPENED';
    }
    updateMutation.mutate({ id: bottle.id, data });
  };

  return (
    <div className="mt-6 bg-surface-2 border border-border rounded-lg p-4">
      <p className="text-xs text-text-mid mb-2">Log a pour</p>
      <div className="flex flex-wrap items-center gap-2">
        <select
          aria-label="Pour size"
          value={String(pourOz)}
          onChange={(e) => setPourOz(Number(e.target.value))}
          className="bg-surface border border-border rounded-md px-2 py-1.5 text-sm text-text-hi focus:outline-none focus:ring-2 focus:ring-primary-bright"
        >
          {POUR_OPTIONS.map((opt) => (
            <option key={opt.oz} value={String(opt.oz)}>
              {opt.label}
            </option>
          ))}
        </select>
        <button
          onClick={logPour}
          disabled={updateMutation.isPending}
          className="flex items-center gap-1 px-3 py-1.5 text-sm text-on-primary bg-primary rounded-md hover:bg-primary-bright disabled:opacity-50"
        >
          <Wine className="w-3.5 h-3.5" />
          {updateMutation.isPending ? 'Logging...' : 'Log pour'}
        </button>
        <span aria-live="polite" className="text-sm text-text-mid">
          {next === 0 ? 'Finishes the bottle' : `Leaves ${next}%`}
        </span>
      </div>
      {updateMutation.isError && (
        <InlineError className="mt-2">Failed to log pour. Try again.</InlineError>
      )}
    </div>
  );
}
