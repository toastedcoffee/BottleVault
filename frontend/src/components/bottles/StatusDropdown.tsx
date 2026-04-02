import { useState, useRef, useEffect } from 'react';
import { ChevronDown, Check, Loader2 } from 'lucide-react';
import { useUpdateBottleStatus } from '../../hooks/useBottles';
import type { BottleStatus } from '../../types/bottle';

const statusOptions: { value: BottleStatus; label: string; classes: string }[] = [
  { value: 'UNOPENED', label: 'Unopened', classes: 'bg-green-100 text-green-800' },
  { value: 'OPENED', label: 'Opened', classes: 'bg-amber-100 text-amber-800' },
  { value: 'EMPTY', label: 'Empty', classes: 'bg-gray-100 text-gray-600' },
];

interface StatusDropdownProps {
  bottleId: string;
  currentStatus: BottleStatus;
}

export default function StatusDropdown({ bottleId, currentStatus }: StatusDropdownProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const mutation = useUpdateBottleStatus();

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [open]);

  const current = statusOptions.find((s) => s.value === currentStatus)!;

  return (
    <div ref={ref} className="relative">
      <button
        onClick={(e) => {
          e.stopPropagation();
          setOpen(!open);
        }}
        className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium cursor-pointer transition-opacity ${current.classes}`}
        disabled={mutation.isPending}
      >
        {current.label}
        {mutation.isPending ? (
          <Loader2 className="w-3 h-3 animate-spin" />
        ) : (
          <ChevronDown className="w-3 h-3" />
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-1 bg-white rounded-lg shadow-lg border border-gray-200 py-1 z-10 min-w-[130px]">
          {statusOptions.map((option) => (
            <button
              key={option.value}
              onClick={(e) => {
                e.stopPropagation();
                if (option.value !== currentStatus) {
                  mutation.mutate({ id: bottleId, status: option.value });
                }
                setOpen(false);
              }}
              className="flex items-center justify-between w-full px-3 py-2 text-sm hover:bg-gray-50 transition-colors"
            >
              <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${option.classes}`}>
                {option.label}
              </span>
              {option.value === currentStatus && (
                <Check className="w-4 h-4 text-primary-600" />
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
