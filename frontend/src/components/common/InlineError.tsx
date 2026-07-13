import { AlertCircle } from 'lucide-react';
import type { ReactNode } from 'react';

interface InlineErrorProps {
  children: ReactNode;
  /** Extra classes for spacing at the call site, e.g. "mb-4" or "mt-2". */
  className?: string;
}

/**
 * Inline error callout. Errors read as errors through FORM — an alert icon plus
 * a tinted container — not color alone. That matters here because pour-red is
 * also the app's accent color (buttons, active nav, links), so red text on its
 * own doesn't signal "error"; it's also the right thing for colorblind users.
 * For full-page/empty error states use ErrorState instead.
 */
export default function InlineError({ children, className = '' }: InlineErrorProps) {
  return (
    <div
      role="alert"
      className={`flex items-start gap-2 p-3 bg-primary/10 border border-primary/40 rounded-md text-sm text-primary-bright ${className}`.trim()}
    >
      <AlertCircle className="w-4 h-4 mt-0.5 flex-none" aria-hidden="true" />
      <span>{children}</span>
    </div>
  );
}
