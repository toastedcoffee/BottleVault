// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import type { BottleStatus } from '../../types/bottle';

const statusConfig: Record<BottleStatus, { label: string; classes: string }> = {
  UNOPENED: { label: 'Unopened', classes: 'bg-white/[0.06] text-text-hi border border-border' },
  OPENED: { label: 'Opened', classes: 'bg-status-opened/15 text-status-opened border border-status-opened/40' },
  EMPTY: { label: 'Empty', classes: 'bg-white/[0.04] text-text-mid border border-border' },
};

export default function StatusBadge({ status }: { status: BottleStatus }) {
  const config = statusConfig[status];
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${config.classes}`}>
      {config.label}
    </span>
  );
}
