import { useEffect, useRef, useState } from 'react';
import { Html5Qrcode, Html5QrcodeScannerState } from 'html5-qrcode';
import { Camera, X } from 'lucide-react';
import InlineError from '../common/InlineError';

interface BarcodeScannerProps {
  onScan: (barcode: string) => void;
  onClose: () => void;
}

type CameraErrorKind = 'denied' | 'notFound' | 'inUse' | 'insecure' | 'unknown';

interface CameraError {
  kind: CameraErrorKind;
  /** Raw error name/message, surfaced so a remote user can read it back to us. */
  detail: string;
}

// scanner.start() rejects for several distinct reasons that all used to render
// as one generic "denied or not available" line — which hides whether the
// camera was blocked, missing, busy, or unavailable (insecure context / in-app
// browser). html5-qrcode forwards getUserMedia's DOMException, so classify by
// its .name and tailor the message — and, for a blocked permission, show how
// to re-enable it.
function classifyCameraError(err: unknown): CameraError {
  const obj = typeof err === 'object' && err !== null ? (err as Record<string, unknown>) : null;
  const name = typeof obj?.name === 'string' ? obj.name : '';
  const message =
    typeof obj?.message === 'string' ? obj.message : typeof err === 'string' ? err : '';
  const haystack = `${name} ${message}`;
  const detail = name || message || 'unknown error';

  if (/NotAllowed|Security|Permission/i.test(haystack)) return { kind: 'denied', detail };
  if (/NotFound|Overconstrained|DevicesNotFound/i.test(haystack))
    return { kind: 'notFound', detail };
  if (/NotReadable|TrackStart|InUse/i.test(haystack)) return { kind: 'inUse', detail };
  if (/TypeError|not supported|mediaDevices|secure/i.test(haystack))
    return { kind: 'insecure', detail };
  return { kind: 'unknown', detail };
}

const ERROR_COPY: Record<CameraErrorKind, { message: string; showEnableSteps: boolean }> = {
  denied: {
    message:
      "Camera access is blocked for this site, so scanning can't start. Allow the camera and reopen the scanner, or enter the barcode manually.",
    showEnableSteps: true,
  },
  notFound: {
    message: 'No camera was found for scanning. Please enter the barcode manually.',
    showEnableSteps: false,
  },
  inUse: {
    message:
      'The camera is in use by another app or browser tab. Close it, then reopen the scanner, or enter the barcode manually.',
    showEnableSteps: false,
  },
  insecure: {
    message:
      "Your browser wouldn't start the camera here. This can happen inside an in-app browser or on an insecure connection. Try opening the site directly in Chrome, Safari, or Firefox. Otherwise, enter the barcode manually.",
    showEnableSteps: false,
  },
  unknown: {
    message:
      'Could not start the camera. Check the camera permission for this site and reopen the scanner, or enter the barcode manually.',
    showEnableSteps: true,
  },
};

export default function BarcodeScanner({ onScan, onClose }: BarcodeScannerProps) {
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const onScanRef = useRef(onScan);
  const stoppingRef = useRef(false);
  const [error, setError] = useState<CameraError | null>(null);
  const [starting, setStarting] = useState(true);
  // Bumped by "Try again" to re-run the start effect after a failure (e.g. once
  // the user has allowed the camera or closed the app that was holding it).
  const [retryToken, setRetryToken] = useState(0);

  // Keep the callback ref current without restarting the scanner. Writing the
  // ref in an effect (not the render body) satisfies react-hooks/refs while
  // still updating before any scan callback fires.
  useEffect(() => {
    onScanRef.current = onScan;
  });

  useEffect(() => {
    const scannerId = 'barcode-reader';
    let mounted = true;

    // Small delay to ensure the DOM element is rendered and sized
    const timer = setTimeout(() => {
      if (!mounted) return;

      const scanner = new Html5Qrcode(scannerId);
      scannerRef.current = scanner;

      scanner
        .start(
          { facingMode: 'environment' },
          {
            fps: 10,
            qrbox: { width: 250, height: 150 },
            aspectRatio: 1.0,
          },
          (decodedText) => {
            // Prevent double-fires
            if (stoppingRef.current) return;
            stoppingRef.current = true;

            // Wait for the scanner to fully stop and clean up its DOM
            // BEFORE triggering onScan (which unmounts this component)
            scanner
              .stop()
              .then(() => {
                scannerRef.current = null;
                onScanRef.current(decodedText);
              })
              .catch(() => {
                scannerRef.current = null;
                onScanRef.current(decodedText);
              });
          },
          () => {} // ignore scan failures (no barcode in frame)
        )
        .then(() => {
          if (mounted) {
            setStarting(false);
          } else {
            // Unmounted while the camera was still being acquired: the
            // cleanup ran while state was still NOT_STARTED and had nothing
            // to stop, so release the camera here instead.
            scanner.stop().catch(() => {});
          }
        })
        .catch((err) => {
          if (mounted) {
            setStarting(false);
            setError(classifyCameraError(err));
          }
          console.error('Scanner error:', err);
        });
    }, 100);

    return () => {
      mounted = false;
      clearTimeout(timer);
      const scanner = scannerRef.current;
      scannerRef.current = null;
      if (scanner && !stoppingRef.current) {
        // stop() throws SYNCHRONOUSLY (not a rejected promise) when the
        // scanner never reached SCANNING — e.g. getUserMedia was denied — so
        // a .catch() alone can't contain it; check the state first.
        const state = scanner.getState();
        if (
          state === Html5QrcodeScannerState.SCANNING ||
          state === Html5QrcodeScannerState.PAUSED
        ) {
          scanner.stop().catch(() => {});
        }
      }
    };
  }, [retryToken]); // Re-run on mount/unmount and whenever "Try again" is pressed

  const handleRetry = () => {
    setError(null);
    setStarting(true);
    stoppingRef.current = false;
    setRetryToken((token) => token + 1);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 flex flex-col items-center justify-center">
      <div className="bg-surface border border-border rounded-lg max-w-md w-full mx-4 overflow-hidden">
        <div className="flex items-center justify-between p-4 border-b border-border">
          <div className="flex items-center gap-2">
            <Camera className="w-5 h-5 text-primary-bright" />
            <h3 className="font-semibold text-text-hi">Scan Barcode</h3>
          </div>
          <button onClick={onClose} className="text-text-low hover:text-text-hi p-1">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-4">
          {error ? (
            <div className="py-6">
              <InlineError>{ERROR_COPY[error.kind].message}</InlineError>

              {ERROR_COPY[error.kind].showEnableSteps && (
                <div className="mt-4 text-sm text-text-mid">
                  <p className="font-medium text-text-hi">How to allow the camera</p>
                  <ol className="mt-2 list-decimal space-y-1 pl-5">
                    <li>Tap the lock or settings icon to the left of the web address.</li>
                    <li>Open Permissions (or Site settings) → Camera.</li>
                    <li>Set Camera to Allow, then reopen the scanner.</li>
                  </ol>
                </div>
              )}

              <div className="mt-4 flex justify-center gap-3">
                <button
                  onClick={onClose}
                  className="px-4 py-2 text-sm font-medium text-text-mid bg-transparent border border-border rounded-md hover:bg-surface-2 hover:text-text-hi"
                >
                  Close
                </button>
                <button
                  onClick={handleRetry}
                  className="px-4 py-2 text-sm font-medium text-on-primary bg-primary rounded-md hover:bg-primary-bright"
                >
                  Try again
                </button>
              </div>

              <p className="mt-4 text-xs text-text-low text-center">Details: {error.detail}</p>
            </div>
          ) : (
            <>
              {starting && (
                <div className="text-center py-4">
                  <p className="text-sm text-text-mid">Starting camera...</p>
                </div>
              )}
              <div
                id="barcode-reader"
                style={{ width: '100%', minHeight: '300px' }}
              />
            </>
          )}
          {!error && (
            <p className="text-xs text-text-mid text-center mt-3">
              Point your camera at a barcode on the bottle
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
