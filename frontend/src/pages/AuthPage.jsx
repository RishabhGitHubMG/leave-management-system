import React, { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";
import SettribeLogo from "../components/SettribeLogo";

// Regex validations
const NAME_REGEX = /^[a-zA-Z\u00C0-\u017F\s'-]{2,100}$/;
const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
const PHONE_REGEX = /^[+\d\s()\-]{7,25}$/;

/** Department options available for employee registration */
const DEPARTMENTS = [
  "Engineering & Technology",
  "Product & Design",
  "Human Resources",
  "Marketing",
  "Sales",
  "Operations",
  "Finance & Accounting",
  "Customer Success",
  "Legal & Compliance",
  "Research & Development",
];

/**
 * Calculates a 4-tier password strength score
 * @param {string} pwd
 * @returns {{ score: number, label: string, color: string }}
 */
function getPasswordStrength(pwd) {
  if (!pwd) return { score: 0, label: "", color: "" };
  let s = 0;
  if (pwd.length >= 8) s += 1;
  if (pwd.length >= 12) s += 1;
  if (/[0-9]/.test(pwd)) s += 1;
  if (/[A-Z]/.test(pwd) && /[^A-Za-z0-9]/.test(pwd)) s += 1;

  if (s <= 1) return { score: 1, label: "Weak", color: "#ef4444" };
  if (s === 2) return { score: 2, label: "Fair", color: "#f59e0b" };
  if (s === 3) return { score: 3, label: "Good", color: "#3b82f6" };
  return { score: 4, label: "Strong", color: "#10b981" };
}

/**
 * AuthPage Component
 *
 * Re-architected dual-mode authentication page for Settribe.
 * Features:
 * - Settribe brand vector header
 * - Sign-out confirmation banner via router state (appears after logout)
 * - Field-level inline validation (Name, RFC Email, Password, Confirm Password)
 * - Extended registration form: Department, Designation, Phone Number, Employee ID
 * - Password strength visual indicator
 * - Accessible eye-toggle for password visibility
 * - Explicit keyboard focus & ARIA error relationships
 */
export default function AuthPage({ mode }) {
  const isLogin = mode === "login";
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // Read logout notice from router state (set when user clicks "Sign out")
  const logoutNotice = location.state?.notice || null;

  // Controlled form state
  const [form, setForm] = useState({
    name: "",
    email: "",
    password: "",
    confirmPassword: "",
    department: "",
    designation: "",
    phoneNumber: "",
    employeeId: "",
  });

  // Field-specific validation errors
  const [errors, setErrors] = useState({});
  // General server/network error banner
  const [serverError, setServerError] = useState("");
  // Busy lock state during active API requests
  const [busy, setBusy] = useState(false);
  // Show / Hide password toggles
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // General field change handler
  const handleChange = (field) => (e) => {
    const val = e.target.value;
    setForm((prev) => ({ ...prev, [field]: val }));
    // Clear field-specific error as user types
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: "" }));
    }
  };

  /**
   * Dedicated handler for Full Name input:
   * Restricts user to strings/characters only (no numbers).
   * If a user types or pastes any number, rejects the digits and immediately alerts them.
   */
  const handleNameChange = (e) => {
    const rawVal = e.target.value;

    // Detect if user typed or pasted digits
    if (/\d/.test(rawVal)) {
      // Strip numbers out so only string/characters remain
      const sanitized = rawVal.replace(/[0-9]/g, "");
      setForm((prev) => ({ ...prev, name: sanitized }));
      // Tell the user immediately
      setErrors((prev) => ({
        ...prev,
        name: "Numbers are not allowed in the name field. Please enter letters only.",
      }));
      return;
    }

    // Normal valid input
    setForm((prev) => ({ ...prev, name: rawVal }));
    if (errors.name) {
      setErrors((prev) => ({ ...prev, name: "" }));
    }
  };

  /**
   * KeyDown handler for Full Name:
   * Intercepts numeric keystrokes (0-9) immediately and informs the user.
   */
  const handleNameKeyDown = (e) => {
    if (/^[0-9]$/.test(e.key)) {
      e.preventDefault();
      setErrors((prev) => ({
        ...prev,
        name: "Numbers are not allowed in the name field. Please enter letters only.",
      }));
    }
  };

  /**
   * Comprehensive client-side validation before dispatch
   */
  const validateForm = () => {
    const errs = {};

    // Validate Name (Registration only)
    if (!isLogin) {
      const trimmedName = form.name.trim();
      if (!trimmedName) {
        errs.name = "Full name is required.";
      } else if (/\d/.test(form.name)) {
        errs.name = "Numbers are not allowed in the name field. Please enter letters only.";
      } else if (!NAME_REGEX.test(trimmedName)) {
        errs.name = "Name must contain 2-100 letters, spaces, hyphens, or apostrophes (no numbers).";
      }
    }

    // Validate Email
    const trimmedEmail = form.email.trim();
    if (!trimmedEmail) {
      errs.email = "Email address is required.";
    } else if (!EMAIL_REGEX.test(trimmedEmail)) {
      errs.email = "Enter a valid email address with a domain (e.g. name@company.com).";
    }

    // Validate Password
    if (!form.password) {
      errs.password = "Password is required.";
    } else if (!isLogin && form.password.length < 8) {
      errs.password = "Password must be at least 8 characters long.";
    }

    // Validate Confirm Password (Registration only)
    if (!isLogin) {
      if (!form.confirmPassword) {
        errs.confirmPassword = "Confirm your password.";
      } else if (form.confirmPassword !== form.password) {
        errs.confirmPassword = "Passwords do not match.";
      }
    }

    // Validate Phone Number (Registration only, if provided)
    if (!isLogin && form.phoneNumber.trim() && !PHONE_REGEX.test(form.phoneNumber.trim())) {
      errs.phoneNumber = "Enter a valid phone number (e.g. +91 98765 43210).";
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  /**
   * Handles Form Submission
   */
  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError("");

    if (!validateForm()) return;

    setBusy(true);
    try {
      const endpoint = isLogin ? "/auth/login" : "/auth/register";
      const payload = isLogin
        ? { email: form.email.trim(), password: form.password }
        : {
            name: form.name.trim(),
            email: form.email.trim(),
            password: form.password,
            department: form.department || null,
            designation: form.designation.trim() || null,
            phoneNumber: form.phoneNumber.trim() || null,
            employeeId: form.employeeId.trim() || null,
          };

      const { data } = await api.post(endpoint, payload);

      // Save user session in AuthContext and navigate to dashboard
      login(data);
      navigate("/dashboard");
    } catch (err) {
      setServerError(errMsg(err));
    } finally {
      setBusy(false);
    }
  };

  const strength = !isLogin ? getPasswordStrength(form.password) : null;

  return (
    <div className="auth-wrapper">
      <Link to="/" style={{ marginBottom: "28px", textDecoration: "none" }} aria-label="Settribe Home">
        <SettribeLogo height={44} />
      </Link>

      <div className="auth-card">
        <header className="auth-header">
          <h1>{isLogin ? "Welcome back" : "Create an account"}</h1>
          <p className="muted">
            {isLogin
              ? "Sign in to manage and review your time-off requests."
              : "Register to submit and track your employee leave requests."}
          </p>
        </header>

        {/* Logout Success Notice Banner — shown after user signs out */}
        {isLogin && logoutNotice && (
          <div
            className="banner ok"
            role="status"
            style={{ marginBottom: "16px", animation: "fadeIn 0.4s ease" }}
          >
            <span>✅</span>
            <span>{logoutNotice}</span>
          </div>
        )}

        {/* Global Server Error Banner */}
        {serverError && (
          <div className="banner error" role="alert">
            <span>⚠️</span>
            <span>{serverError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          {/* Name Field (Register Mode Only) */}
          {!isLogin && (
            <div className="form-group">
              <label htmlFor="auth-name" className="form-label">
                Full Name <span style={{ color: "#ef4444" }}>*</span>
              </label>
              <input
                id="auth-name"
                type="text"
                className={`form-input ${errors.name ? "is-invalid" : ""}`}
                value={form.name}
                onChange={handleNameChange}
                onKeyDown={handleNameKeyDown}
                placeholder="Jane Doe (letters only)"
                autoComplete="name"
                disabled={busy}
                aria-invalid={!!errors.name}
                aria-describedby={errors.name ? "name-error" : undefined}
              />

              {errors.name && (
                <div id="name-error" className="field-error" role="alert">
                  {errors.name}
                </div>
              )}
            </div>
          )}

          {/* Email Field */}
          <div className="form-group">
            <label htmlFor="auth-email" className="form-label">
              Work Email Address <span style={{ color: "#ef4444" }}>*</span>
            </label>
            <input
              id="auth-email"
              type="email"
              className={`form-input ${errors.email ? "is-invalid" : ""}`}
              value={form.email}
              onChange={handleChange("email")}
              placeholder="jane.doe@settribe.com"
              autoComplete="email"
              disabled={busy}
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? "email-error" : undefined}
            />
            {errors.email && (
              <div id="email-error" className="field-error" role="alert">
                {errors.email}
              </div>
            )}
          </div>

          {/* Password Field */}
          <div className="form-group">
            <label htmlFor="auth-password" className="form-label">
              Password <span style={{ color: "#ef4444" }}>*</span>
            </label>
            <div className="input-password-wrap">
              <input
                id="auth-password"
                type={showPassword ? "text" : "password"}
                className={`form-input ${errors.password ? "is-invalid" : ""}`}
                value={form.password}
                onChange={handleChange("password")}
                placeholder="••••••••"
                autoComplete={isLogin ? "current-password" : "new-password"}
                disabled={busy}
                aria-invalid={!!errors.password}
                aria-describedby={errors.password ? "password-error" : undefined}
              />
              <button
                type="button"
                className="btn-password-toggle"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? "Hide password" : "Show password"}
                tabIndex="-1"
              >
                {showPassword ? "👁️" : "👁️‍🗨️"}
              </button>
            </div>
            {errors.password && (
              <div id="password-error" className="field-error" role="alert">
                {errors.password}
              </div>
            )}

            {/* Password Strength Indicator (Register only) */}
            {!isLogin && form.password && (
              <div style={{ marginTop: "8px" }}>
                <div style={{ display: "flex", gap: "4px", height: "4px" }}>
                  {[1, 2, 3, 4].map((level) => (
                    <div
                      key={level}
                      style={{
                        flex: 1,
                        borderRadius: "2px",
                        backgroundColor:
                          strength.score >= level ? strength.color : "var(--line-strong)",
                        transition: "background-color 0.2s ease",
                      }}
                    />
                  ))}
                </div>
                <div style={{ fontSize: "0.75rem", color: strength.color, marginTop: "4px", fontWeight: "600" }}>
                  Strength: {strength.label}
                </div>
              </div>
            )}
          </div>

          {/* Confirm Password Field (Register Mode Only) */}
          {!isLogin && (
            <div className="form-group">
              <label htmlFor="auth-confirm-password" className="form-label">
                Confirm Password <span style={{ color: "#ef4444" }}>*</span>
              </label>
              <div className="input-password-wrap">
                <input
                  id="auth-confirm-password"
                  type={showConfirmPassword ? "text" : "password"}
                  className={`form-input ${errors.confirmPassword ? "is-invalid" : ""}`}
                  value={form.confirmPassword}
                  onChange={handleChange("confirmPassword")}
                  placeholder="••••••••"
                  autoComplete="new-password"
                  disabled={busy}
                  aria-invalid={!!errors.confirmPassword}
                  aria-describedby={errors.confirmPassword ? "confirm-password-error" : undefined}
                />
                <button
                  type="button"
                  className="btn-password-toggle"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  aria-label={showConfirmPassword ? "Hide password" : "Show password"}
                  tabIndex="-1"
                >
                  {showConfirmPassword ? "👁️" : "👁️‍🗨️"}
                </button>
              </div>
              {errors.confirmPassword && (
                <div id="confirm-password-error" className="field-error" role="alert">
                  {errors.confirmPassword}
                </div>
              )}
            </div>
          )}

          {/* ---- Extended Profile Fields (Register Mode Only) ---- */}
          {!isLogin && (
            <>
              <div
                style={{
                  margin: "20px 0 12px",
                  padding: "10px 14px",
                  background: "var(--surface-raised)",
                  borderRadius: "8px",
                  borderLeft: "3px solid var(--brand-accent)",
                }}
              >
                <p style={{ margin: 0, fontSize: "0.8125rem", fontWeight: 600, color: "var(--ink-primary)" }}>
                  Professional Profile (Optional)
                </p>
                <p style={{ margin: "4px 0 0", fontSize: "0.75rem", color: "var(--ink-muted)" }}>
                  Help your manager identify you and track leave by department.
                </p>
              </div>

              {/* Department Selector */}
              <div className="form-group">
                <label htmlFor="auth-department" className="form-label">
                  Department
                </label>
                <select
                  id="auth-department"
                  className="form-select"
                  value={form.department}
                  onChange={handleChange("department")}
                  disabled={busy}
                  style={{ width: "100%" }}
                >
                  <option value="">Select your department...</option>
                  {DEPARTMENTS.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
              </div>

              {/* Designation / Job Title */}
              <div className="form-group">
                <label htmlFor="auth-designation" className="form-label">
                  Job Title / Designation
                </label>
                <input
                  id="auth-designation"
                  type="text"
                  className="form-input"
                  value={form.designation}
                  onChange={handleChange("designation")}
                  placeholder="e.g. Senior Developer, Team Lead"
                  autoComplete="organization-title"
                  disabled={busy}
                  maxLength={100}
                />
              </div>

              {/* Two-column row for Phone & Employee ID */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px" }}>
                {/* Phone Number */}
                <div className="form-group">
                  <label htmlFor="auth-phone" className="form-label">
                    Phone Number
                  </label>
                  <input
                    id="auth-phone"
                    type="tel"
                    className={`form-input ${errors.phoneNumber ? "is-invalid" : ""}`}
                    value={form.phoneNumber}
                    onChange={handleChange("phoneNumber")}
                    placeholder="+91 98765 43210"
                    autoComplete="tel"
                    disabled={busy}
                    aria-invalid={!!errors.phoneNumber}
                    aria-describedby={errors.phoneNumber ? "phone-error" : undefined}
                  />
                  {errors.phoneNumber && (
                    <div id="phone-error" className="field-error" role="alert">
                      {errors.phoneNumber}
                    </div>
                  )}
                </div>

                {/* Employee ID */}
                <div className="form-group">
                  <label htmlFor="auth-employee-id" className="form-label">
                    Employee ID
                  </label>
                  <input
                    id="auth-employee-id"
                    type="text"
                    className="form-input"
                    value={form.employeeId}
                    onChange={handleChange("employeeId")}
                    placeholder="Auto-assigned if blank"
                    disabled={busy}
                    maxLength={50}
                  />
                  <div style={{ fontSize: "0.7rem", color: "var(--ink-muted)", marginTop: "4px" }}>
                    Assigned as SET-XXXX if left empty
                  </div>
                </div>
              </div>
            </>
          )}

          {/* Submit Button */}
          <button type="submit" className="btn btn-accent" style={{ width: "100%", marginTop: "16px" }} disabled={busy}>
            {busy ? (
              <span>⏳ {isLogin ? "Signing in..." : "Creating account..."}</span>
            ) : isLogin ? (
              "Sign In to Dashboard →"
            ) : (
              "Complete Registration →"
            )}
          </button>
        </form>

        <footer className="auth-footer-link">
          {isLogin ? (
            <p className="muted">
              Don't have an account? <Link to="/register" style={{ fontWeight: 600 }}>Create account</Link>
            </p>
          ) : (
            <p className="muted">
              Already registered? <Link to="/login" style={{ fontWeight: 600 }}>Sign in</Link>
            </p>
          )}
        </footer>
      </div>
    </div>
  );
}
