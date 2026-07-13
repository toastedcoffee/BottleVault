import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import InlineError from './InlineError';

describe('InlineError', () => {
  it('renders its message inside an assertive alert region', () => {
    render(<InlineError>Something failed</InlineError>);

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Something failed');
  });

  it('includes a decorative icon (form signal, not color alone)', () => {
    const { container } = render(<InlineError>Oops</InlineError>);
    // lucide renders an <svg>; it is aria-hidden so it is not announced.
    const icon = container.querySelector('svg');
    expect(icon).toBeInTheDocument();
    expect(icon).toHaveAttribute('aria-hidden', 'true');
  });

  it('applies caller spacing classes', () => {
    render(<InlineError className="mb-4">x</InlineError>);
    expect(screen.getByRole('alert').className).toContain('mb-4');
  });
});
