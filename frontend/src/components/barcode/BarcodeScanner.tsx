import { useEffect, useRef, useState } from 'react';
import { Html5Qrcode } from 'html5-qrcode';
import { Camera, X } from 'lucide-react';

interface BarcodeScannerProps {
  onScan: (barcode: string) => void;
  onClose: () => void;
}

export default function BarcodeScanner({ onScan, onClose }: BarcodeScannerProps) {
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const scanner = new Html5Qrcode('barcode-reader');
    scannerRef.current = scanner;

    scanner
      .start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 150 } },
        (decodedText) => {
          scanner.stop().catch(() => {});
          onScan(decodedText);
        },
        () => {} // ignore scan failures (no barcode in frame)
      )
      .catch((err) => {
        setError('Camera access denied or not available. Please enter the barcode manually.');
        console.error('Scanner error:', err);
      });

    return () => {
      scanner.stop().catch(() => {});
    };
  }, [onScan]);

  return (
    <div className="fixed inset-0 z-50 bg-black/80 flex flex-col items-center justify-center">
      <div className="bg-white rounded-lg max-w-md w-full mx-4 overflow-hidden">
        <div className="flex items-center justify-between p-4 border-b">
          <div className="flex items-center gap-2">
            <Camera className="w-5 h-5 text-primary-600" />
            <h3 className="font-semibold">Scan Barcode</h3>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-4">
          {error ? (
            <p className="text-sm text-red-600 text-center py-8">{error}</p>
          ) : (
            <div id="barcode-reader" className="w-full" />
          )}
          <p className="text-xs text-gray-500 text-center mt-3">
            Point your camera at a barcode on the bottle
          </p>
        </div>
      </div>
    </div>
  );
}
