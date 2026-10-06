import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";

export default function AuthPage({ mode }) {
  const isLogin = mode === "login";
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: "", email: "", password: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    if (!isLogin && !form.name.trim()) return setError("Enter your name.");
    if (!form.email.trim()) return setError("Enter your email.");
    if (!isLogin && form.password.length < 8) return setError("Password must be at least 8 characters.");
    if (isLogin && !form.password) return setError("Enter your password.");

    setBusy(true);
    try {
      const body = isLogin ? { email: form.email, password: form.password } : form;
      const { data } = await api.post(isLogin ? "/auth/login" : "/auth/register", body);
      login(data);
      navigate("/dashboard");
    } catch (err) {
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
        {error && <div className="banner error" role="alert">{error}</div>}
        {!isLogin && (
          <label>Name<input value={form.name} onChange={set("name")} autoComplete="name" /></label>
        )}
        <label>Email<input type="email" value={form.email} onChange={set("email")} autoComplete="email" /></label>
        <label>Password<input type="password" value={form.password} onChange={set("password")}
          autoComplete={isLogin ? "current-password" : "new-password"} /></label>
        <button className="primary" disabled={busy}>
          {busy ? "Please wait..." : isLogin ? "Sign in" : "Create account"}
        </button>
        <p className="muted center">
          {isLogin ? <>No account? <Link to="/register">Register</Link></> : <>Already registered? <Link to="/login">Sign in</Link></>}
        </p>
      </form>
    </main>
  );
}
