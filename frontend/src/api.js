import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api",
});

// Attach the JWT to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Expired or invalid token on a protected call: clear the session and go to login
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

export function errMsg(e) {
  if (!e.response) return "Can't reach the server. Check that the backend is running.";
  const d = e.response.data;
  if (d?.errors && Object.keys(d.errors).length) return Object.values(d.errors).join(". ");
  return d?.message || "Something went wrong. Try again.";
}

export default api;
