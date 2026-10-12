import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth";
import LandingPage from "./pages/LandingPage";
import AuthPage from "./pages/AuthPage";
import Dashboard from "./pages/Dashboard";

/**
 * Protected Route Wrapper:
 * Ensures the wrapped route is only accessible to authenticated users.
 * If user is not authenticated, redirects automatically to the /login page.
 */
function Protected({ children }) {
  const { user } = useAuth();
  return user ? children : <Navigate to="/login" replace />;
}

/**
 * Guest-Only Route Wrapper:
 * Restricts access to unauthenticated users (e.g. login & register pages).
 * If user is already logged in, redirects them directly to /dashboard.
 */
function GuestOnly({ children }) {
  const { user } = useAuth();
  return user ? <Navigate to="/dashboard" replace /> : children;
}

/**
 * Main application component configuring client-side routing.
 */
export default function App() {
  return (
    <Routes>
      {/* Public Landing Page */}
      <Route path="/" element={<LandingPage />} />

      {/* Guest-only Auth routes */}
      <Route path="/login" element={<GuestOnly><AuthPage mode="login" /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><AuthPage mode="register" /></GuestOnly>} />

      {/* Authenticated Dashboard route */}
      <Route path="/dashboard" element={<Protected><Dashboard /></Protected>} />

      {/* Catch-all fallback route */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
