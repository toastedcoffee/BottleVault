import { Search } from 'lucide-react';
import type { BottleStatus } from '../../types/bottle';

const STATUSES: { value: BottleStatus | ''; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'UNOPENED', label: 'Unopened' },
  { value: 'OPENED', label: 'Opened' },
  { value: 'EMPTY', label: 'Empty' },
];

const TYPES = [
  '', 'WHISKEY', 'BOURBON', 'SCOTCH', 'RYE', 'VODKA', 'GIN', 'RUM',
  'TEQUILA', 'BRANDY', 'COGNAC', 'WINE_RED', 'WINE_WHITE', 'BEER',
  'LIQUEUR', 'OTHER',
];

interface BottleFiltersProps {
  search: string;
  status: BottleStatus | '';
  type: string;
  onSearchChange: (value: string) => void;
  onStatusChange: (value: BottleStatus | '') => void;
  onTypeChange: (value: string) => void;
}

export default function BottleFilters({
  search, status, type,
  onSearchChange, onStatusChange, onTypeChange,
}: BottleFiltersProps) {
  return (
    <div className="flex flex-col sm:flex-row gap-3">
      <div className="relative flex-1">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
        <input
          type="text"
          placeholder="Search bottles..."
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
        />
      </div>

      <select
        value={status}
        onChange={(e) => onStatusChange(e.target.value as BottleStatus | '')}
        className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-primary-500"
      >
        {STATUSES.map((s) => (
          <option key={s.value} value={s.value}>{s.label}</option>
        ))}
      </select>

      <select
        value={type}
        onChange={(e) => onTypeChange(e.target.value)}
        className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-primary-500"
      >
        <option value="">All Types</option>
        {TYPES.filter(Boolean).map((t) => (
          <option key={t} value={t}>{t.replace('_', ' ')}</option>
        ))}
      </select>
    </div>
  );
}
