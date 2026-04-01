import { useEffect, useRef, useState } from 'react';
import { Html5Qrcode } from 'html5-qrcode';
import { Camera, X } from 'lucide-react';

interface BarcodeScannerProps {
  onScan: (barcode: string) => void;
  onClose: () => void;
}

export default function BarcodeScanner({ onScan, onClose }: BarcodeScannerProps) {
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const onScanRef = useRef(onScan);
  const stoppingRef = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [starting, setStarting] = useState(true);

  // Keep the callback ref current without restarting the scanner
  onScanRef.current = onScan;

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
          if (mounted) setStarting(false);
        })
        .catch((err) => {
          if (mounted) {
            setStarting(false);
            setError(
              'Camera access denied or not available. Please enter the barcode manually.'
            );
          }
          console.error('Scanner error:', err);
        });
    }, 100);

    return () => {
      mounted = false;
      clearTimeout(timer);
      if (scannerRef.current && !stoppingRef.current) {
        scannerRef.current.stop().catch(() => {});
        scannerRef.current = null;
      }
    };
  }, []); // No dependencies — only mount/unmount

  return (
    <div className="fixed inset-0 z-50 bg-black/80 flex flex-col items-center justify-center">
      <div className="bg-white rounded-lg max-w-md w-full mx-4 overflow-hidden">
        <div className="flex items-center justify-between p-4 border-b">
          <div className="flex items-center gap-2">
            <Camera className="w-5 h-5 text-primary-600" />
            <h3 className="font-semibold">Scan Barcode</h3>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-4">
          {error ? (
            <div className="text-center py-8">
              <p className="text-sm text-red-600">{error}</p>
              <button
                onClick={onClose}
                className="mt-4 px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
              >
                Close
              </button>
            </div>
          ) : (
            <>
              {starting && (
                <div className="text-center py-4">
                  <p className="text-sm text-gray-500">Starting camera...</p>
                </div>
              )}
              <div
                id="barcode-reader"
                style={{ width: '100%', minHeight: '300px' }}
              />
            </>
          )}
          <p className="text-xs text-gray-500 text-center mt-3">
            Point your camera at a barcode on the bottle
          </p>
        </div>
      </div>
    </div>
  );
}
