import { useEffect } from "react";
import { lazy, Suspense } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./auth";
import LandingPage from "./pages/LandingPage";

const AuthPage = lazy(() => import("./pages/AuthPage"));
const Dashboard = lazy(() => import("./pages/Dashboard"));
const AdminUsers = lazy(() => import("./pages/AdminUsers"));

/**
 * Protected Route Wrapper:
 * Ensures the wrapped route is only accessible to authenticated users.
 * If user is not authenticated, redirects automatically to the /login page.
 */
function Protected({ children }) {
  const { user } = useAuth();
  const location = useLocation();
  return user ? children : <Navigate to="/login" state={{ from: location }} replace />;
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
  useEffect(() => {
    document.documentElement.setAttribute(
      "data-theme",
      localStorage.getItem("theme") === "dark" ? "dark" : "light"
    );
  }, []);

  return (
    <Suspense fallback={<div role="status">Loading…</div>}>
      <Routes>
        {/* Public Landing Page */}
        <Route path="/" element={<LandingPage />} />

        {/* Guest-only Auth routes */}
        <Route path="/login" element={<GuestOnly><AuthPage mode="login" /></GuestOnly>} />
        <Route path="/register" element={<GuestOnly><AuthPage mode="register" /></GuestOnly>} />

        {/* Authenticated Dashboard route */}
        <Route path="/dashboard" element={<Protected><Dashboard /></Protected>} />
        <Route path="/admin/users" element={<Protected><AdminUsers /></Protected>} />

        {/* Catch-all fallback route */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
