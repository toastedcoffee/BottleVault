import { useSyncExternalStore } from 'react';
import { AlertTriangle } from 'lucide-react';
import { getServiceState, subscribeServiceState } from '../../lib/serviceState';

function waitCopy(retryAfterSeconds: number | null): string {
  if (retryAfterSeconds === null) return 'We should be back shortly.';
  // Round up: a 30s hint rendering as "about 0 minutes" reads as broken.
  const minutes = Math.max(1, Math.round(retryAfterSeconds / 60));
  return `We should be back in about ${minutes} ${minutes === 1 ? 'minute' : 'minutes'}.`;
}

/**
 * Renders only while the API is unreachable. The state is written by the axios
 * response interceptor (see api/client.ts), which lives outside the component
 * tree — hence useSyncExternalStore rather than context.
 */
export default function ServiceBanner() {
  const { unavailable, retryAfterSeconds } = useSyncExternalStore(
    subscribeServiceState,
    getServiceState
  );

  if (!unavailable) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-start gap-2 rounded-md border border-amber-500/40 bg-amber-500/10 px-4 py-3 mb-6 text-sm text-text-hi"
    >
      <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0 text-amber-400" aria-hidden="true" />
      <span>
        <strong className="font-medium">BottleVault is updating.</strong>{' '}
        {waitCopy(retryAfterSeconds)} Your collection is safe — changes you make right now
        won&rsquo;t be saved.
      </span>
    </div>
  );
}
