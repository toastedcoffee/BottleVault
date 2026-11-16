// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
export interface LoginRequest {
  email: string;
  password: string;
  turnstileToken?: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName?: string;
  inviteCode?: string;
  turnstileToken?: string;
}

export type RegistrationMode = 'open' | 'invite' | 'closed';

/** Public settings from GET /api/auth/config, read before anyone signs in. */
export interface AuthConfig {
  registrationMode: RegistrationMode;
  turnstileSiteKey?: string | null;
  privacyUrl?: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: UserProfile;
}

export interface UserProfile {
  id: string;
  email: string;
  displayName: string | null;
  defaultCurrency: string;
  measurementUnit: string;
}
