// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { AlertTriangle } from 'lucide-react';

interface ErrorStateProps {
  message?: string;
  onRetry?: () => void;
}

export default function ErrorState({
  message = 'Something went wrong',
  onRetry,
}: ErrorStateProps) {
  return (
    <div className="text-center py-16">
      <AlertTriangle className="w-12 h-12 text-primary-bright mx-auto" />
      <h3 className="mt-4 text-lg font-medium text-text-hi">{message}</h3>
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-4 inline-flex items-center px-4 py-2 bg-primary text-on-primary text-sm font-medium rounded-lg hover:bg-primary-bright transition-colors"
        >
          Try Again
        </button>
      )}
    </div>
  );
}
