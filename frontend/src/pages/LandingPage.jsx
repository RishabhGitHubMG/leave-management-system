import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import SettribeLogo from "../components/SettribeLogo";

/**
 * Public Landing Page Component
 * 
 * Commercial-grade enterprise landing page for Settribe Leave Management System.
 * Establishes Settribe brand identity, illustrates employee and admin workflows,
 * features a realistic interactive dashboard preview, and provides direct CTAs.
 */
export default function LandingPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  // Mobile navigation hamburger state
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  // Interactive dashboard preview mode toggle: 'employee' or 'admin'
  const [previewTab, setPreviewTab] = useState("employee");

  return (
    <div className="landing-page">
      {/* --------------------------------------------------------------------
         A. Premium Navigation Bar
         -------------------------------------------------------------------- */}
      <nav className="site-nav" aria-label="Main Navigation">
        <div className="nav-container">
          <Link to="/" className="nav-brand" aria-label="Settribe Home">
            <SettribeLogo height={38} />
          </Link>

          {/* Desktop Navigation Links */}
          <ul className={`nav-links ${mobileMenuOpen ? "mobile-open" : ""}`}>
            <li>
              <a href="#features" className="nav-link" onClick={() => setMobileMenuOpen(false)}>
                Features
              </a>
            </li>
            <li>
              <a href="#how-it-works" className="nav-link" onClick={() => setMobileMenuOpen(false)}>
                How It Works
              </a>
            </li>
            <li>
              <a href="#preview" className="nav-link" onClick={() => setMobileMenuOpen(false)}>
                Preview
              </a>
            </li>
            <li>
              <a href="#security" className="nav-link" onClick={() => setMobileMenuOpen(false)}>
                Security
              </a>
            </li>
          </ul>

          {/* Nav Actions / User Session */}
          <div className="nav-actions">
            {user ? (
              <>
                <Link to="/dashboard" className="btn btn-primary btn-sm">
                  Go to Dashboard
                </Link>
                <button onClick={logout} className="btn btn-ghost btn-sm" aria-label="Sign out">
                  Sign out
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="btn btn-ghost btn-sm">
                  Sign In
                </Link>
                <Link to="/register" className="btn btn-accent btn-sm">
                  Get Started
                </Link>
              </>
            )}

            {/* Mobile Hamburger Button */}
            <button
              className="nav-mobile-toggle"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              aria-label="Toggle navigation menu"
              aria-expanded={mobileMenuOpen}
            >
              {mobileMenuOpen ? "✕" : "☰"}
            </button>
          </div>
        </div>
      </nav>

      {/* --------------------------------------------------------------------
         B. Hero Section with Compelling Copy & Interactive Preview
         -------------------------------------------------------------------- */}
      <header className="landing-hero">
        <div className="hero-pill">
          <span>🚀</span> Official Settribe Enterprise Portal
        </div>

        <h1 className="hero-title">
          Leave Management, <br />
          <span className="hero-title-highlight">Made Simple.</span>
        </h1>

        <p className="hero-subtitle">
          Streamline leave requests, simplify approvals, and give your team a clearer view
          of time off — all in one centralized, high-performance platform.
        </p>

        <div className="hero-cta-group">
          {user ? (
            <Link to="/dashboard" className="btn btn-accent btn-lg">
              Launch Dashboard →
            </Link>
          ) : (
            <>
              <Link to="/register" className="btn btn-accent btn-lg">
                Get Started Free →
              </Link>
              <Link to="/login" className="btn btn-secondary btn-lg">
                Sign In to Portal
              </Link>
            </>
          )}
        </div>

        {/* Realistic Dashboard Preview Frame */}
        <div className="hero-mockup-wrapper" id="preview">
          <div className="mockup-header-bar">
            <span className="mockup-dot red"></span>
            <span className="mockup-dot yellow"></span>
            <span className="mockup-dot green"></span>
            <div style={{ marginLeft: "auto", display: "flex", gap: "8px" }}>
              <button
                className={`btn btn-sm ${previewTab === "employee" ? "btn-primary" : "btn-ghost"}`}
                onClick={() => setPreviewTab("employee")}
              >
                Employee View
              </button>
              <button
                className={`btn btn-sm ${previewTab === "admin" ? "btn-primary" : "btn-ghost"}`}
                onClick={() => setPreviewTab("admin")}
              >
                Admin Review View
              </button>
            </div>
          </div>

          <div className="mockup-body">
            {/* Mockup Metrics Cards */}
            <div className="metrics-grid">
              <div className="metric-card">
                <div className="metric-header">
                  <span className="metric-title">Annual Leave</span>
                  <span className="metric-icon">🏖️</span>
                </div>
                <div className="metric-value">12 / 18</div>
                <span className="metric-subtitle">Days remaining this cycle</span>
              </div>
              <div className="metric-card">
                <div className="metric-header">
                  <span className="metric-title">Sick Leave</span>
                  <span className="metric-icon">🏥</span>
                </div>
                <div className="metric-value">8 / 10</div>
                <span className="metric-subtitle">Paid medical days</span>
              </div>
              <div className="metric-card">
                <div className="metric-header">
                  <span className="metric-title">Pending Review</span>
                  <span className="metric-icon">⏳</span>
                </div>
                <div className="metric-value">1</div>
                <span className="metric-subtitle">Awaiting managerial approval</span>
              </div>
              <div className="metric-card">
                <div className="metric-header">
                  <span className="metric-title">Approved Days</span>
                  <span className="metric-icon">✅</span>
                </div>
                <div className="metric-value">6</div>
                <span className="metric-subtitle">Completed time off</span>
              </div>
            </div>

            {/* Mockup Leave Items */}
            <div className="requests-list">
              <div className="request-card">
                <div className="request-main">
                  <div className="request-title-row">
                    <span className="request-type-name">Annual Leave</span>
                    <span className="badge badge-pending">Pending Review</span>
                    <span className="chip">4 Working Days</span>
                  </div>
                  <div className="request-date-row">
                    <span>📅 Oct 20, 2026 to Oct 24, 2026</span>
                    {previewTab === "admin" && <span>👤 Sarah Jenkins (sarah@settribe.com)</span>}
                  </div>
                  <p className="request-reason">
                    Attending professional development conference and taking scheduled annual vacation.
                  </p>
                </div>
                <div className="request-actions">
                  {previewTab === "employee" ? (
                    <>
                      <button className="btn btn-secondary btn-sm" disabled>Edit Request</button>
                      <button className="btn btn-danger-outline btn-sm" disabled>Cancel</button>
                    </>
                  ) : (
                    <>
                      <button className="btn btn-accent btn-sm" disabled>Approve</button>
                      <button className="btn btn-danger-outline btn-sm" disabled>Reject</button>
                    </>
                  )}
                </div>
              </div>

              <div className="request-card">
                <div className="request-main">
                  <div className="request-title-row">
                    <span className="request-type-name">Sick Leave</span>
                    <span className="badge badge-approved">Approved</span>
                    <span className="chip">2 Days</span>
                  </div>
                  <div className="request-date-row">
                    <span>📅 Oct 02, 2026 to Oct 03, 2026</span>
                    {previewTab === "admin" && <span>👤 Sarah Jenkins (sarah@settribe.com)</span>}
                  </div>
                  <p className="request-reason">
                    Medical recovery appointment; physician certificate submitted to HR.
                  </p>
                </div>
                <div className="request-actions">
                  <span className="badge badge-approved">Verified by Admin</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* --------------------------------------------------------------------
         C. Key Benefits Section
         -------------------------------------------------------------------- */}
      <section className="landing-section" id="features">
        <div className="center-head">
          <span className="section-badge">Why Settribe</span>
          <h2 className="section-title">Everything You Need for Seamless Time-Off</h2>
          <p className="section-desc">
            Designed specifically for modern organizations seeking clarity, speed, and
            transparency across workforce schedules.
          </p>
        </div>

        <div className="benefits-grid">
          <div className="benefit-card">
            <div className="benefit-icon-box">⚡</div>
            <h3>Simplified Applications</h3>
            <p>
              Employees can apply for leave in seconds with intuitive date range pickers,
              real-time duration counters, and automatic calendar checks.
            </p>
          </div>

          <div className="benefit-card">
            <div className="benefit-icon-box">🎯</div>
            <h3>Fast Manager Approvals</h3>
            <p>
              Administrators review pending requests in a unified inbox, approving or
              rejecting applications with one click and providing transparent notes.
            </p>
          </div>

          <div className="benefit-card">
            <div className="benefit-icon-box">📊</div>
            <h3>Transparent Tracking</h3>
            <p>
              Eliminate guesswork. Employees track their leave status in real-time,
              viewing pending, approved, and rejected history instantly.
            </p>
          </div>

          <div className="benefit-card">
            <div className="benefit-icon-box">🛡️</div>
            <h3>Overlap & Conflict Defense</h3>
            <p>
              Intelligent backend validation prevents duplicate applications and overlapping
              leave periods, protecting schedule accuracy.
            </p>
          </div>

          <div className="benefit-card">
            <div className="benefit-icon-box">👥</div>
            <h3>Role-Based Permissions</h3>
            <p>
              Strict separation between Employee and Administrator roles ensures sensitive
              records are only visible to authorized personnel.
            </p>
          </div>

          <div className="benefit-card">
            <div className="benefit-icon-box">📱</div>
            <h3>Responsive & Accessible</h3>
            <p>
              Fully mobile-responsive interface compliant with WCAG 2.2 AA standards,
              ensuring accessible experiences across all devices and screen sizes.
            </p>
          </div>
        </div>
      </section>

      {/* --------------------------------------------------------------------
         D. How It Works (Step-by-Step)
         -------------------------------------------------------------------- */}
      <section className="landing-section" id="how-it-works">
        <div className="center-head">
          <span className="section-badge">Simple Process</span>
          <h2 className="section-title">How It Works in 4 Easy Steps</h2>
          <p className="section-desc">
            Experience a frictionless journey from initial registration to approved time off.
          </p>
        </div>

        <div className="steps-grid">
          <div className="step-card">
            <div className="step-number">1</div>
            <h3>Create an Account</h3>
            <p>
              Register securely as an employee or sign in with your corporate credentials using
              industry-standard JWT token authorization.
            </p>
          </div>

          <div className="step-card">
            <div className="step-number">2</div>
            <h3>Submit Request</h3>
            <p>
              Choose your leave type (Sick, Casual, Annual, or Unpaid), select valid dates,
              and enter your reason with live character feedback.
            </p>
          </div>

          <div className="step-card">
            <div className="step-number">3</div>
            <h3>Track Live Status</h3>
            <p>
              Monitor your request in your personal dashboard. Need to make an update?
              Edit or cancel your application while it remains in pending state.
            </p>
          </div>

          <div className="step-card">
            <div className="step-number">4</div>
            <h3>Admin Decision</h3>
            <p>
              Managers evaluate company-wide requests with comprehensive employee context and
              finalize decisions promptly.
            </p>
          </div>
        </div>
      </section>

      {/* --------------------------------------------------------------------
         F. Security & Trust Section
         -------------------------------------------------------------------- */}
      <section className="landing-section" id="security">
        <div className="security-box">
          <div>
            <span className="section-badge">Enterprise Reliability</span>
            <h2 className="section-title">Built with Security & Data Integrity at the Core</h2>
            <p style={{ marginBottom: "24px" }}>
              Settribe's Leave Management System employs enterprise-grade architecture
              to safeguard sensitive workforce information.
            </p>
            <ul className="security-list">
              <li className="security-item">
                <span className="security-check">✔</span>
                <div>
                  <strong>Stateless JWT Authentication:</strong> Every request is securely validated
                  using cryptographic HMAC-SHA256 tokens with automatic expiration.
                </div>
              </li>
              <li className="security-item">
                <span className="security-check">✔</span>
                <div>
                  <strong>BCrypt Password Hashing:</strong> Credentials are never stored in plain text,
                  protected with salt-reinforced cryptographic algorithms.
                </div>
              </li>
              <li className="security-item">
                <span className="security-check">✔</span>
                <div>
                  <strong>Role-Based Access Control:</strong> Server-side filters enforce strict
                  separation between Employee and Administrator capabilities.
                </div>
              </li>
              <li className="security-item">
                <span className="security-check">✔</span>
                <div>
                  <strong>ID Probing Protection:</strong> Unauthorized access attempts return 404
                  Not Found rather than revealing valid record IDs.
                </div>
              </li>
            </ul>
          </div>

          <div style={{ display: "flex", justifyContent: "center" }}>
            <div
              style={{
                background: "var(--bg-surface-muted)",
                padding: "32px",
                borderRadius: "var(--radius-lg)",
                border: "1px solid var(--line-strong)",
                maxWidth: "380px",
                textAlign: "center",
              }}
            >
              <div style={{ fontSize: "3rem", marginBottom: "12px" }}>🔒</div>
              <h3 style={{ marginBottom: "8px" }}>Zero Plaintext Exposure</h3>
              <p style={{ fontSize: "0.875rem" }}>
                All sensitive transactions, authorization checks, and data operations run strictly
                under Spring Security 6 verification.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* --------------------------------------------------------------------
         G. Final Call-to-Action Section
         -------------------------------------------------------------------- */}
      <section className="cta-banner">
        <h2>Make Leave Management Easier for Everyone.</h2>
        <p>
          Bring leave applications, approvals, and request tracking into one organized,
          reliable workflow with Settribe.
        </p>
        <div style={{ display: "flex", justifyContent: "center", gap: "16px", flexWrap: "wrap" }}>
          {user ? (
            <Link to="/dashboard" className="btn btn-accent btn-lg">
              Return to Dashboard →
            </Link>
          ) : (
            <>
              <Link to="/register" className="btn btn-accent btn-lg">
                Get Started Today
              </Link>
              <Link to="/login" className="btn btn-secondary btn-lg">
                Sign In
              </Link>
            </>
          )}
        </div>
      </section>

      {/* --------------------------------------------------------------------
         H. Professional Footer
         -------------------------------------------------------------------- */}
      <footer className="site-footer">
        <div className="footer-container">
          <div className="footer-top">
            <div className="footer-brand-info">
              <SettribeLogo height={36} />
              <p className="muted">
                Settribe LLP — An Ed-Tech Prudentia. Providing intuitive, dependable software
                solutions for modern workplaces.
              </p>
            </div>

            <div className="footer-nav-col">
              <h4>Navigation</h4>
              <ul>
                <li><a href="#features">Features</a></li>
                <li><a href="#how-it-works">How It Works</a></li>
                <li><a href="#preview">Preview</a></li>
                <li><a href="#security">Security</a></li>
              </ul>
            </div>

            <div className="footer-nav-col">
              <h4>Application</h4>
              <ul>
                <li><Link to="/login">Employee Login</Link></li>
                <li><Link to="/register">Create Account</Link></li>
                <li><Link to="/dashboard">Dashboard</Link></li>
              </ul>
            </div>
          </div>

          <div className="footer-bottom">
            <span>© 2026 SETTribe LLP. All rights reserved.</span>
            <span>Employee Leave Management System v1.2</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
