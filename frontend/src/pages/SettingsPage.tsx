import { useState } from 'react';
import { useAuth } from '../context/useAuth';
import { useUpdateProfile, useChangePassword } from '../hooks/useUser';
import InlineError from '../components/common/InlineError';

const inputClasses = 'w-full px-3 py-2 bg-surface border border-border rounded-md text-sm text-text-hi placeholder:text-text-low focus:outline-none focus:ring-2 focus:ring-primary-bright focus:border-primary-bright';
const labelClasses = 'block text-sm font-medium text-text-mid mb-1';
const buttonClasses = 'py-2 px-4 bg-primary text-on-primary text-sm font-medium rounded-md hover:bg-primary-bright disabled:opacity-50 disabled:cursor-not-allowed';

function getErrorMessage(err: unknown): string {
  if (typeof err === 'object' && err !== null && 'response' in err) {
    const axiosErr = err as { response?: { data?: { message?: string } } };
    return axiosErr.response?.data?.message || 'Something went wrong';
  }
  return err instanceof Error ? err.message : 'Something went wrong';
}

export default function SettingsPage() {
  const { user, updateUser } = useAuth();
  const updateProfile = useUpdateProfile();
  const changePassword = useChangePassword();

  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [currency, setCurrency] = useState(user?.defaultCurrency || 'USD');
  const [unit, setUnit] = useState(user?.measurementUnit || 'ml');
  const [profileSuccess, setProfileSuccess] = useState(false);
  const [profileError, setProfileError] = useState('');

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState(false);
  const [passwordError, setPasswordError] = useState('');

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileSuccess(false);
    setProfileError('');

    try {
      const updated = await updateProfile.mutateAsync({
        displayName: displayName || undefined,
        defaultCurrency: currency,
        measurementUnit: unit,
      });
      updateUser(updated);
      setProfileSuccess(true);
    } catch (err) {
      setProfileError(getErrorMessage(err));
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordSuccess(false);
    setPasswordError('');

    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match');
      return;
    }

    try {
      await changePassword.mutateAsync({ currentPassword, newPassword });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setPasswordSuccess(true);
    } catch (err) {
      setPasswordError(getErrorMessage(err));
    }
  };

  return (
    <div className="max-w-2xl mx-auto space-y-8">
      <h1 className="font-display text-2xl font-bold text-text-hi">Settings</h1>

      {/* Profile Section */}
      <div className="bg-surface rounded-lg border border-border p-6">
        <h2 className="text-lg font-semibold text-text-hi mb-4">Profile</h2>

        {profileSuccess && (
          <div className="mb-4 p-3 bg-gold/10 border border-gold/40 rounded-md text-sm text-gold">
            Profile updated successfully.
          </div>
        )}
        {profileError && (
          <InlineError className="mb-4">{profileError}</InlineError>
        )}

        <form onSubmit={handleProfileSubmit} className="space-y-4">
          <div>
            <label className={labelClasses}>Email</label>
            <input
              type="email"
              value={user?.email || ''}
              disabled
              className={`${inputClasses} bg-surface-2 text-text-low cursor-not-allowed`}
            />
          </div>

          <div>
            <label className={labelClasses}>Display Name</label>
            <input
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className={inputClasses}
              placeholder="Your name"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClasses}>Currency</label>
              <select
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
                className={inputClasses}
              >
                <option value="USD">USD ($)</option>
                <option value="EUR">EUR (&euro;)</option>
                <option value="GBP">GBP (&pound;)</option>
                <option value="CAD">CAD ($)</option>
                <option value="AUD">AUD ($)</option>
                <option value="JPY">JPY (&yen;)</option>
              </select>
            </div>

            <div>
              <label className={labelClasses}>Measurement Unit</label>
              <select
                value={unit}
                onChange={(e) => setUnit(e.target.value)}
                className={inputClasses}
              >
                <option value="ml">Milliliters (ml)</option>
                <option value="oz">Ounces (oz)</option>
                <option value="cl">Centiliters (cl)</option>
              </select>
            </div>
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={updateProfile.isPending}
              className={buttonClasses}
            >
              {updateProfile.isPending ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </form>
      </div>

      {/* Password Section */}
      <div className="bg-surface rounded-lg border border-border p-6">
        <h2 className="text-lg font-semibold text-text-hi mb-4">Change Password</h2>

        {passwordSuccess && (
          <div className="mb-4 p-3 bg-gold/10 border border-gold/40 rounded-md text-sm text-gold">
            Password updated successfully.
          </div>
        )}
        {passwordError && (
          <InlineError className="mb-4">{passwordError}</InlineError>
        )}

        <form onSubmit={handlePasswordSubmit} className="space-y-4">
          <div>
            <label className={labelClasses}>Current Password</label>
            <input
              type="password"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className={inputClasses}
            />
          </div>

          <div>
            <label className={labelClasses}>New Password</label>
            <input
              type="password"
              required
              minLength={8}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className={inputClasses}
              placeholder="At least 8 characters"
            />
          </div>

          <div>
            <label className={labelClasses}>Confirm New Password</label>
            <input
              type="password"
              required
              minLength={8}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className={inputClasses}
            />
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={changePassword.isPending}
              className={buttonClasses}
            >
              {changePassword.isPending ? 'Updating...' : 'Update Password'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
