// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useState } from 'react';
import { useAuth } from '../context/useAuth';
import { Wine } from 'lucide-react';
import InlineError from '../components/common/InlineError';
import SourceOffer from '../components/common/SourceOffer';

export default function LoginPage() {
  const { login, register } = useAuth();
  const [isRegistering, setIsRegistering] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      if (isRegistering) {
        await register({ email, password, displayName: displayName || undefined });
      } else {
        await login({ email, password });
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Something went wrong';
      if (typeof err === 'object' && err !== null && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message || message);
      } else {
        setError(message);
      }
    } finally {
      setLoading(false);
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
            {isRegistering && (
              <div>
                <label className="block text-sm font-medium text-text-mid mb-1">Display Name</label>
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                  placeholder="Your name"
                />
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-text-mid mb-1">Email</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                placeholder="you@example.com"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-text-mid mb-1">Password</label>
              <input
                type="password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright"
                placeholder={isRegistering ? 'At least 8 characters' : 'Your password'}
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2 px-4 bg-primary text-on-primary text-sm font-medium rounded-md hover:bg-primary-bright disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Please wait...' : isRegistering ? 'Create Account' : 'Sign In'}
            </button>
          </form>

          <div className="mt-4 text-center">
            <button
              onClick={() => { setIsRegistering(!isRegistering); setError(''); }}
              className="text-sm text-primary-bright hover:text-primary"
            >
              {isRegistering ? 'Already have an account? Sign in' : "Don't have an account? Register"}
            </button>
          </div>
        </div>

        <div className="mt-6">
          <SourceOffer />
        </div>
      </div>
    </div>
  );
}
