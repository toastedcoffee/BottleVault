import type { BottleStatus } from '../../types/bottle';

const statusConfig: Record<BottleStatus, { label: string; classes: string }> = {
  UNOPENED: { label: 'Unopened', classes: 'bg-green-100 text-green-800' },
  OPENED: { label: 'Opened', classes: 'bg-amber-100 text-amber-800' },
  EMPTY: { label: 'Empty', classes: 'bg-gray-100 text-gray-600' },
};

export default function StatusBadge({ status }: { status: BottleStatus }) {
  const config = statusConfig[status];
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${config.classes}`}>
      {config.label}
    </span>
  );
}
