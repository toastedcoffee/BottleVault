import { Suspense, useState } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';
import { Wine, LogOut, Plus, Menu, X, Settings, BarChart3 } from 'lucide-react';
import LoadingSpinner from '../common/LoadingSpinner';
import ServiceBanner from '../common/ServiceBanner';

export default function AppLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  // Close the mobile menu on any route change. Done during render (React's
  // "adjust state when a value changes" pattern) rather than in an effect —
  // avoids react-hooks/set-state-in-effect and the extra commit an effect adds.
  const [menuPath, setMenuPath] = useState(location.pathname);
  if (menuPath !== location.pathname) {
    setMenuPath(location.pathname);
    setMobileMenuOpen(false);
  }

  const navLinks = [
    { to: '/inventory', label: 'Inventory', icon: Wine },
    { to: '/statistics', label: 'Statistics', icon: BarChart3 },
    { to: '/inventory/add', label: 'Add Bottle', icon: Plus },
    { to: '/settings', label: 'Settings', icon: Settings },
  ];

  return (
    <div className="min-h-screen bg-bg">
      <nav className="bg-bg border-b border-border">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center gap-8">
              <Link to="/inventory" className="flex items-center gap-2">
                <Wine className="w-6 h-6 text-primary" />
                <span className="font-display font-semibold text-xl tracking-tight">
                  <span className="text-text-hi">Bottle</span><span className="text-primary-bright">Vault</span>
                </span>
              </Link>
              <div className="hidden sm:flex gap-1">
                {navLinks.map((link) => {
                  const Icon = link.icon;
                  const isActive = location.pathname === link.to;
                  return (
                    <Link
                      key={link.to}
                      to={link.to}
                      className={`flex items-center gap-1.5 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                        isActive
                          ? 'bg-primary/15 text-primary-bright'
                          : 'text-text-mid hover:text-text-hi hover:bg-surface'
                      }`}
                    >
                      <Icon className="w-4 h-4" />
                      {link.label}
                    </Link>
                  );
                })}
              </div>
            </div>
            <div className="flex items-center gap-4">
              <span className="hidden sm:inline text-sm text-text-mid">
                {user?.displayName || user?.email}
              </span>
              <button
                onClick={logout}
                className="hidden sm:flex items-center gap-1.5 px-3 py-2 text-sm text-text-mid hover:text-text-hi rounded-md hover:bg-surface transition-colors"
              >
                <LogOut className="w-4 h-4" />
                Logout
              </button>
              <button
                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                className="sm:hidden flex items-center justify-center min-w-[44px] min-h-[44px] text-text-mid hover:text-text-hi rounded-md hover:bg-surface transition-colors"
                aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
              >
                {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
              </button>
            </div>
          </div>
        </div>

        {mobileMenuOpen && (
          <div className="sm:hidden border-t border-border bg-surface">
            <div className="px-4 py-2 space-y-1">
              {navLinks.map((link) => {
                const Icon = link.icon;
                const isActive = location.pathname === link.to;
                return (
                  <Link
                    key={link.to}
                    to={link.to}
                    className={`flex items-center gap-2 px-3 py-3 rounded-md text-sm font-medium min-h-[44px] transition-colors ${
                      isActive
                        ? 'bg-primary/15 text-primary-bright'
                        : 'text-text-mid hover:text-text-hi hover:bg-surface-2'
                    }`}
                  >
                    <Icon className="w-5 h-5" />
                    {link.label}
                  </Link>
                );
              })}
            </div>
            <div className="border-t border-border px-4 py-3">
              <p className="text-sm text-text-mid mb-2">
                {user?.displayName || user?.email}
              </p>
              <button
                onClick={logout}
                className="flex items-center gap-2 w-full px-3 py-3 text-sm font-medium text-text-mid hover:text-text-hi rounded-md hover:bg-surface-2 min-h-[44px] transition-colors"
              >
                <LogOut className="w-5 h-5" />
                Logout
              </button>
            </div>
          </div>
        )}
      </nav>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <ServiceBanner />
        {/* Single boundary for all lazy protected pages: the nav above stays
            mounted, so only the content area shows a spinner (no white flash). */}
        <Suspense fallback={<LoadingSpinner className="min-h-[50vh]" />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}
