import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";

/**
 * Dual-purpose Authentication Page component.
 * Supports both "login" and "register" modes based on the mode prop.
 * 
 * @param {string} mode - Either "login" or "register".
 */
export default function AuthPage({ mode }) {
  const isLogin = mode === "login";
  const { login } = useAuth();
  const navigate = useNavigate();

  // Controlled form state for user inputs
  const [form, setForm] = useState({ name: "", email: "", password: "" });
  // Error message displayed in banner alert
  const [error, setError] = useState("");
  // Busy indicator to disable submit button during active API call
  const [busy, setBusy] = useState(false);

  // Helper to update specific form field state
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  /**
   * Handles form submission:
   * 1. Validates inputs on the client side (name, email, minimum password length).
   * 2. Sends POST request to /auth/login or /auth/register.
   * 3. Stores session and redirects to /dashboard upon success.
   */
  const submit = async (e) => {
    e.preventDefault();
    setError("");

    // Client-side validations
    if (!isLogin && !form.name.trim()) return setError("Enter your name.");
    if (!form.email.trim()) return setError("Enter your email.");
    if (!isLogin && form.password.length < 8) return setError("Password must be at least 8 characters.");
    if (isLogin && !form.password) return setError("Enter your password.");

    setBusy(true);
    try {
      // Build request body (registration requires name, login only email & password)
      const body = isLogin ? { email: form.email, password: form.password } : form;
      const { data } = await api.post(isLogin ? "/auth/login" : "/auth/register", body);

      // Save user session in AuthContext and navigate to dashboard
      login(data);
      navigate("/dashboard");
    } catch (err) {
      // Display formatted error message from backend
      setError(errMsg(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth">
      <form className="panel" onSubmit={submit} noValidate>
        <h1>{isLogin ? "Sign in" : "Create your account"}</h1>
        <p className="muted">{isLogin ? "Manage your leave requests." : "Employees can apply for leave and track it."}</p>

        {/* Dynamic Error Banner */}
        {error && <div className="banner error" role="alert">{error}</div>}

        {/* Name input (only shown in register mode) */}
        {!isLogin && (
          <label>Name<input value={form.name} onChange={set("name")} autoComplete="name" /></label>
        )}

        {/* Email input */}
        <label>Email<input type="email" value={form.email} onChange={set("email")} autoComplete="email" /></label>

        {/* Password input */}
        <label>Password<input type="password" value={form.password} onChange={set("password")}
          autoComplete={isLogin ? "current-password" : "new-password"} /></label>

        {/* Submit button with loading state */}
        <button className="primary" disabled={busy}>
          {busy ? "Please wait..." : isLogin ? "Sign in" : "Create account"}
        </button>

        {/* Navigation toggle between Login and Register */}
        <p className="muted center">
          {isLogin ? <>No account? <Link to="/register">Register</Link></> : <>Already registered? <Link to="/login">Sign in</Link></>}
        </p>
      </form>
    </main>
  );
}

