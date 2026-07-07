import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './context/AuthContext';
import { useAuth } from './context/useAuth';
import AppLayout from './components/layout/AppLayout';
import LoadingSpinner from './components/common/LoadingSpinner';
import ErrorBoundary from './components/common/ErrorBoundary';

// Route pages are lazy-loaded so each becomes its own chunk instead of
// bloating the entry bundle. AddBottlePage in particular kept html5-qrcode
// (via BarcodeScanner) out of the main bundle only once it's lazy.
const LoginPage = lazy(() => import('./pages/LoginPage'));
const InventoryPage = lazy(() => import('./pages/InventoryPage'));
const AddBottlePage = lazy(() => import('./pages/AddBottlePage'));
const EditBottlePage = lazy(() => import('./pages/EditBottlePage'));
const BottleDetailPage = lazy(() => import('./pages/BottleDetailPage'));
const StatisticsPage = lazy(() => import('./pages/StatisticsPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
});

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return <LoadingSpinner className="min-h-screen" />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function AppRoutes() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) return <LoadingSpinner className="min-h-screen" />;

  return (
    <Routes>
      <Route
        path="/login"
        element={
          isAuthenticated ? (
            <Navigate to="/inventory" replace />
          ) : (
            <Suspense fallback={<LoadingSpinner className="min-h-screen" />}>
              <LoginPage />
            </Suspense>
          )
        }
      />
      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/inventory" element={<InventoryPage />} />
        <Route path="/inventory/add" element={<AddBottlePage />} />
        <Route path="/inventory/:id" element={<BottleDetailPage />} />
        <Route path="/inventory/:id/edit" element={<EditBottlePage />} />
        <Route path="/statistics" element={<StatisticsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/inventory" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <ErrorBoundary>
            <AppRoutes />
          </ErrorBoundary>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
