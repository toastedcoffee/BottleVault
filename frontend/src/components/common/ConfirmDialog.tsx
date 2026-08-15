// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Delete',
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4">
      <div className="bg-surface border border-border rounded-lg shadow-xl max-w-md w-full p-6">
        <h3 className="text-lg font-semibold text-text-hi">{title}</h3>
        <p className="mt-2 text-sm text-text-mid">{message}</p>
        <div className="mt-6 flex justify-end gap-3">
          <button
            onClick={onCancel}
            className="px-4 py-2 text-sm font-medium text-text-mid bg-transparent border border-border rounded-md hover:bg-surface-2 hover:text-text-hi"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            className="px-4 py-2 text-sm font-medium text-on-primary bg-primary rounded-md hover:bg-primary-bright"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
