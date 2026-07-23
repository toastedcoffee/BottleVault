import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import BarcodeScanner from './BarcodeScanner';

const mockScanner = vi.hoisted(() => ({
  startBehavior: 'resolve' as 'resolve' | 'reject' | 'hang',
  // Error start() rejects with when startBehavior is 'reject'. Defaults to a
  // denied-permission DOMException; individual tests override it to exercise
  // the other classified failure modes.
  startError: null as unknown,
  resolveStart: null as (() => void) | null,
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
          mockScanner.startError ??
            new DOMException('Permission denied', 'NotAllowedError')
        );
      }
      if (mockScanner.startBehavior === 'hang') {
        // Camera acquisition in flight: state stays NOT_STARTED (the real
        // library's state transaction executes only on success) until the
        // test resolves the start promise.
        return new Promise((resolve) => {
          mockScanner.resolveStart = () => {
            this.state = Html5QrcodeScannerState.SCANNING;
            resolve();
          };
        });
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
    mockScanner.startError = null;
    mockScanner.resolveStart = null;
    mockScanner.stopCalls = 0;
  });

  it('shows re-enable guidance and closes cleanly when camera permission is denied', async () => {
    mockScanner.startBehavior = 'reject'; // defaults to NotAllowedError
    const { unmount } = render(
      <BarcodeScanner onScan={() => {}} onClose={() => {}} />
    );

    await screen.findByText(/camera access is blocked/i);
    expect(screen.getByText(/how to allow the camera/i)).toBeInTheDocument();
    // The raw error name is surfaced so a remote user can read it back to us.
    expect(screen.getByText(/^Details:/).textContent).toContain('NotAllowedError');

    // Before the fix, the effect cleanup called stop() on a scanner that
    // never started, and the synchronous throw escaped to the ErrorBoundary.
    expect(() => unmount()).not.toThrow();
  });

  it('shows a distinct message with no re-enable steps when no camera is found', async () => {
    mockScanner.startBehavior = 'reject';
    mockScanner.startError = new DOMException('No device', 'NotFoundError');
    render(<BarcodeScanner onScan={() => {}} onClose={() => {}} />);

    await screen.findByText(/no camera was found/i);
    expect(screen.queryByText(/how to allow the camera/i)).not.toBeInTheDocument();
    expect(screen.getByText(/^Details:/).textContent).toContain('NotFoundError');
  });

  it('restarts the camera when "Try again" is pressed after a failure', async () => {
    mockScanner.startBehavior = 'reject'; // defaults to NotAllowedError
    render(<BarcodeScanner onScan={() => {}} onClose={() => {}} />);

    await screen.findByText(/camera access is blocked/i);

    // Camera now allowed: the next start() succeeds.
    mockScanner.startBehavior = 'resolve';
    fireEvent.click(screen.getByRole('button', { name: /try again/i }));

    // The error UI clears and the scanner remounts its reader element.
    await waitFor(() =>
      expect(screen.queryByText(/camera access is blocked/i)).not.toBeInTheDocument()
    );
    expect(document.getElementById('barcode-reader')).not.toBeNull();
  });

  it('treats a missing mediaDevices (TypeError) as an insecure-context problem', async () => {
    mockScanner.startBehavior = 'reject';
    mockScanner.startError = new TypeError(
      "Cannot read properties of undefined (reading 'getUserMedia')"
    );
    render(<BarcodeScanner onScan={() => {}} onClose={() => {}} />);

    await screen.findByText(/in-app browser or on an insecure connection/i);
    expect(screen.queryByText(/how to allow the camera/i)).not.toBeInTheDocument();
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

  it('releases the camera when unmounted while start is still pending', async () => {
    mockScanner.startBehavior = 'hang';
    const { unmount } = render(
      <BarcodeScanner onScan={() => {}} onClose={() => {}} />
    );

    // Wait past the 100ms mount delay until start() has been called.
    await waitFor(() => expect(mockScanner.resolveStart).not.toBeNull());

    // Close the overlay before camera acquisition finishes: state is still
    // NOT_STARTED, so cleanup must not call stop() (it would throw)...
    expect(() => unmount()).not.toThrow();
    expect(mockScanner.stopCalls).toBe(0);

    // ...and when acquisition then succeeds, the camera must be released.
    mockScanner.resolveStart!();
    await waitFor(() => expect(mockScanner.stopCalls).toBe(1));
  });
});
