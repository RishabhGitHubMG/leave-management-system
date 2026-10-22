import axios from "axios";

/**
 * Determine the backend API base URL:
 * - If running on Render (*.onrender.com), routes to the live deployed Render backend API.
 * - Otherwise uses the relative /api path, which the Vite dev server proxies to the local backend.
 * - Can be overridden explicitly by the VITE_API_URL environment variable.
 */
const defaultUrl = typeof window !== "undefined" && window.location.hostname.includes("onrender.com")
  ? "https://leave-management-system-1-9uph.onrender.com/api"
  : "/api";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || defaultUrl,
  timeout: 15000, // 15 second request timeout
});


/**
 * Request Interceptor:
 * Automatically extracts the stored JWT token from localStorage and attaches it
 * to the HTTP Authorization header as a 'Bearer <token>' on every outgoing API call.
 */
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

/**
 * Response Interceptor:
 * Detects 401 Unauthorized responses on protected endpoints (e.g. expired or invalid tokens).
 * Automatically clears client session from localStorage and redirects user to /login.
 */
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const isAuthCall = err.config?.url?.startsWith("/auth");
    if (err.response?.status === 401 && !isAuthCall) {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      window.location.href = "/login";
    }
    return Promise.reject(err);
  }
);

/**
 * Helper function to extract a user-friendly error message from an API failure:
 * - Network failures / backend offline.
 * - Field-level validation error maps from GlobalExceptionHandler.
 * - General backend error message string.
 */
export function errMsg(e) {
  if (e.code === "ECONNABORTED" || e.message?.toLowerCase().includes("timeout")) {
    return "Request timed out. The server took too long to respond. Please try again.";
  }
  if (!e.response) {
    return "Can't reach the server. Please check your network connection or verify that the backend is running.";
  }
  const d = e.response.data;
  if (d?.errors && Object.keys(d.errors).length) return Object.values(d.errors).join(". ");
  return d?.message || "Something went wrong. Try again.";
}


export default api;
