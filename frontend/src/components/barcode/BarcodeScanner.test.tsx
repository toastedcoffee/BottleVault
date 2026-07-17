import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import BarcodeScanner from './BarcodeScanner';

const mockScanner = vi.hoisted(() => ({
  startBehavior: 'resolve' as 'resolve' | 'reject',
  stopCalls: 0,
}));

// Mirrors the real html5-qrcode behavior that matters here: stop() throws
// SYNCHRONOUSLY (a string, not a rejected promise) when the scanner is not
// in SCANNING or PAUSED state — e.g. after start() rejected because
// getUserMedia was denied. See node_modules/html5-qrcode/esm/html5-qrcode.js.
vi.mock('html5-qrcode', () => {
  const Html5QrcodeScannerState = {
    UNKNOWN: 0,
    NOT_STARTED: 1,
    SCANNING: 2,
    PAUSED: 3,
  } as const;

  class Html5Qrcode {
    private state: number = Html5QrcodeScannerState.NOT_STARTED;

    start(): Promise<void> {
      if (mockScanner.startBehavior === 'reject') {
        return Promise.reject(
          new DOMException('Permission denied', 'NotAllowedError')
        );
      }
      this.state = Html5QrcodeScannerState.SCANNING;
      return Promise.resolve();
    }

    getState(): number {
      return this.state;
    }

    stop(): Promise<void> {
      if (
        this.state !== Html5QrcodeScannerState.SCANNING &&
        this.state !== Html5QrcodeScannerState.PAUSED
      ) {
        throw 'Cannot stop, scanner is not running or paused.';
      }
      mockScanner.stopCalls += 1;
      this.state = Html5QrcodeScannerState.NOT_STARTED;
      return Promise.resolve();
    }
  }

  return { Html5Qrcode, Html5QrcodeScannerState };
});

describe('BarcodeScanner', () => {
  beforeEach(() => {
    mockScanner.startBehavior = 'resolve';
    mockScanner.stopCalls = 0;
  });

  it('shows the permission message and closes cleanly when camera access is denied', async () => {
    mockScanner.startBehavior = 'reject';
    const { unmount } = render(
      <BarcodeScanner onScan={() => {}} onClose={() => {}} />
    );

    await screen.findByText(/camera access denied/i);

    // Before the fix, the effect cleanup called stop() on a scanner that
    // never started, and the synchronous throw escaped to the ErrorBoundary.
    expect(() => unmount()).not.toThrow();
  });

  it('stops the scanner on unmount after a successful start', async () => {
    const { unmount } = render(
      <BarcodeScanner onScan={() => {}} onClose={() => {}} />
    );

    await waitFor(() =>
      expect(screen.queryByText(/starting camera/i)).not.toBeInTheDocument()
    );

    unmount();
    expect(mockScanner.stopCalls).toBe(1);
  });
});
