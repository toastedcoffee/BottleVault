// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect } from 'vitest';
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { describeApiError } from './client';

function axiosError(status: number, data: unknown = {}, headers: Record<string, string> = {}): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig;
  const response = { status, data, headers, statusText: '', config } as AxiosResponse;
  return new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', config, null, response);
}

describe('describeApiError', () => {
  it('prefers the server message', () => {
    expect(describeApiError(axiosError(400, { message: 'Invalid or expired invite code' }))).toBe('Invalid or expired invite code');
  });

  it('turns Retry-After into minutes', () => {
    expect(describeApiError(axiosError(429, {}, { 'retry-after': '600' }))).toBe('Too many attempts. Try again in 10 minutes.');
    expect(describeApiError(axiosError(429, {}, { 'retry-after': '30' }))).toBe('Too many attempts. Try again in 1 minute.');
  });

  it('handles an nginx 429 page with no header', () => {
    expect(describeApiError(axiosError(429, '<html>…</html>'))).toBe('Too many attempts. Please wait a bit and try again.');
  });

  it('knows the upload statuses', () => {
    expect(describeApiError(axiosError(413, '<html>'))).toBe('That file is too large.');
    expect(describeApiError(axiosError(415, {}))).toBe("That file type isn't supported.");
    expect(describeApiError(axiosError(507, {}))).toBe('Storage is full right now. Please try again later.');
  });

  it('falls back sensibly', () => {
    expect(describeApiError(new Error('boom'))).toBe('boom');
    expect(describeApiError(axiosError(500, {}), 'Could not save')).toBe('Could not save');
    expect(describeApiError('weird')).toBe('Something went wrong. Please try again.');
  });
});
