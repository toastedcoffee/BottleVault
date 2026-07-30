import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import ServiceBanner from './ServiceBanner';
import { markUnavailable, markAvailable } from '../../lib/serviceState';

beforeEach(() => {
  markAvailable();
});

describe('ServiceBanner', () => {
  it('renders nothing while the service is available', () => {
    const { container } = render(<ServiceBanner />);

    expect(container).toBeEmptyDOMElement();
  });

  it('appears when the service goes unavailable after mount', () => {
    render(<ServiceBanner />);

    act(() => markUnavailable(null));

    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('states a wait time when one was advertised', () => {
    render(<ServiceBanner />);

    act(() => markUnavailable(900));

    expect(screen.getByRole('status')).toHaveTextContent('about 15 minutes');
  });

  it('rounds a sub-minute hint up to one minute rather than showing zero', () => {
    render(<ServiceBanner />);

    act(() => markUnavailable(30));

    expect(screen.getByRole('status')).toHaveTextContent('about 1 minute');
  });

  it('falls back to generic copy with no hint', () => {
    render(<ServiceBanner />);

    act(() => markUnavailable(null));

    expect(screen.getByRole('status')).toHaveTextContent('back shortly');
  });

  it('disappears once the service recovers', () => {
    render(<ServiceBanner />);
    act(() => markUnavailable(60));
    expect(screen.getByRole('status')).toBeInTheDocument();

    act(() => markAvailable());

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
