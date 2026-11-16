// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useEffect, useState } from 'react';
import { useAuth } from '../context/useAuth';
import { useAuthConfig } from '../hooks/useAuthConfig';
import { describeApiError } from '../api/client';
import { readInviteFromHash } from '../lib/invite';
import { Wine } from 'lucide-react';
import InlineError from '../components/common/InlineError';
import SourceOffer from '../components/common/SourceOffer';
import TurnstileWidget from '../components/auth/TurnstileWidget';

const inputClasses =
  'w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright';

/** Only http(s) links are rendered: a misconfigured PRIVACY_URL must never become a javascript: link. */
function safeHttpUrl(value?: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.toString() : null;
  } catch {
    return null;
  }
}

interface LoginPageProps {
  initialMode?: 'login' | 'register';
}

export default function LoginPage({ initialMode = 'login' }: LoginPageProps) {
  const { login, register } = useAuth();
  const { data: config } = useAuthConfig();
  // Read once, on first render: an invite link opens registration with the code filled in.
  const [inviteFromLink] = useState(() => readInviteFromHash(window.location.hash));
  const [isRegistering, setIsRegistering] = useState(initialMode === 'register' || inviteFromLink !== null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [inviteCode, setInviteCode] = useState(inviteFromLink ?? '');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null);
  const [turnstileReset, setTurnstileReset] = useState(0);

  // Then take the code out of the address bar and history.
  useEffect(() => {
    if (inviteFromLink !== null) {
      window.history.replaceState(null, '', window.location.pathname + window.location.search);
    }
  }, [inviteFromLink]);

  const mode = config?.registrationMode;
  const canRegister = mode !== 'closed';
  const needsInvite = mode === 'invite' || inviteFromLink !== null;
  const privacyHref = safeHttpUrl(config?.privacyUrl);
  const siteKey = config?.turnstileSiteKey || null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (siteKey && !turnstileToken) {
      setError('Please complete the verification.');
      return;
    }
    setLoading(true);
    try {
      if (isRegistering) {
        await register({
          email,
          password,
          displayName: displayName || undefined,
          inviteCode: needsInvite ? inviteCode.trim() || undefined : undefined,
          turnstileToken: turnstileToken ?? undefined,
        });
      } else {
        await login({ email, password, turnstileToken: turnstileToken ?? undefined });
      }
    } catch (err: unknown) {
      setError(describeApiError(err));
    } finally {
      setLoading(false);
      // Tokens are single-use: whatever happened, the next attempt needs a new one.
      if (siteKey) {
        setTurnstileToken(null);
        setTurnstileReset((k) => k + 1);
      }
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-b from-bg to-bg-2 px-4">
      <div className="max-w-sm w-full">
        <div className="text-center mb-8">
          <Wine className="w-12 h-12 text-primary mx-auto" />
          <h1 className="font-display mt-4 text-2xl font-bold">
            <span className="text-text-hi">Bottle</span><span className="text-primary-bright">Vault™</span>
          </h1>
          <p className="mt-1 text-sm text-text-mid">Manage your bottle collection</p>
        </div>

        <div className="bg-surface rounded-lg border border-border p-6">
          <h2 className="text-lg font-semibold text-text-hi mb-4">
            {isRegistering ? 'Create Account' : 'Sign In'}
          </h2>

          {error && (
            <InlineError className="mb-4">{error}</InlineError>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {isRegistering && needsInvite && (
              <div>
                <label htmlFor="invite-code" className="block text-sm font-medium text-text-mid mb-1">Invite code</label>
                <input
                  id="invite-code"
                  type="text"
                  required
                  autoComplete="off"
                  maxLength={64}
                  value={inviteCode}
                  onChange={(e) => setInviteCode(e.target.value)}
                  className={inputClasses}
                  placeholder="XXXX-XXXX-XXXX-XXXX"
                />
              </div>
            )}

            {isRegistering && (
              <div>
                <label htmlFor="display-name" className="block text-sm font-medium text-text-mid mb-1">Display Name</label>
                <input
                  id="display-name"
                  type="text"
                  maxLength={100}
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className={inputClasses}
                  placeholder="Your name"
                />
              </div>
            )}

            <div>
              <label htmlFor="email" className="block text-sm font-medium text-text-mid mb-1">Email</label>
              <input
                id="email"
                type="email"
                required
                maxLength={254}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputClasses}
                placeholder="you@example.com"
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-text-mid mb-1">Password</label>
              <input
                id="password"
                type="password"
                required
                minLength={8}
                maxLength={72}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={inputClasses}
                placeholder={isRegistering ? '8 to 72 characters' : 'Your password'}
              />
            </div>

            {siteKey && (
              <TurnstileWidget
                key={isRegistering ? 'register' : 'login'}
                siteKey={siteKey}
                action={isRegistering ? 'register' : 'login'}
                onToken={setTurnstileToken}
                resetKey={turnstileReset}
              />
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2 px-4 bg-primary text-on-primary text-sm font-medium rounded-md hover:bg-primary-bright disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Please wait...' : isRegistering ? 'Create Account' : 'Sign In'}
            </button>
          </form>

          {isRegistering && privacyHref && (
            <p className="mt-3 text-xs text-text-mid text-center">
              By creating an account you agree to the{' '}
              <a href={privacyHref} target="_blank" rel="noopener noreferrer" className="text-primary-bright hover:text-primary">
                Privacy notice
              </a>
              .
            </p>
          )}

          {canRegister && (
            <div className="mt-4 text-center">
              <button
                type="button"
                onClick={() => { setIsRegistering(!isRegistering); setError(''); }}
                className="text-sm text-primary-bright hover:text-primary"
              >
                {isRegistering ? 'Already have an account? Sign in' : "Don't have an account? Register"}
              </button>
            </div>
          )}
        </div>

        <div className="mt-6">
          <SourceOffer />
        </div>
      </div>
    </div>
  );
}
