import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";
import SettribeLogo from "../components/SettribeLogo";
import LeaveForm, { TYPES, label } from "../components/LeaveForm";
import ConfirmModal from "../components/ConfirmModal";

const STATUSES = ["PENDING", "APPROVED", "REJECTED"];

/**
 * Calculates calendar day count between two dates inclusive
 */
function calculateDays(from, to) {
  if (!from || !to) return 1;
  const d1 = new Date(from);
  const d2 = new Date(to);
  const diffTime = d2.getTime() - d1.getTime();
  if (diffTime < 0) return 1;
  return Math.round(diffTime / (1000 * 3600 * 24)) + 1;
}

/**
 * Returns icon corresponding to leave category
 */
function getLeaveTypeIcon(type) {
  switch (type) {
    case "SICK": return "🏥";
    case "ANNUAL": return "🏖️";
    case "CASUAL": return "🌴";
    case "UNPAID": return "📋";
    default: return "🗓️";
  }
}

/**
 * Returns icon corresponding to status
 */
function getStatusIcon(status) {
  switch (status) {
    case "PENDING": return "⏳";
    case "APPROVED": return "✅";
    case "REJECTED": return "❌";
    default: return "•";
  }
}

// =============================================================================
// Rejection Reason Modal — Prompts admin for mandatory rejection reason
// =============================================================================
/**
 * Modal dialog that captures the admin's rejection reason before submitting.
 * Enforces minimum 5-character reason to match backend validation.
 *
 * @param {{ open: boolean, item: object|null, busy: boolean, onConfirm: (remarks: string) => void, onCancel: () => void }} props
 */
function RejectModal({ open, item, busy, onConfirm, onCancel }) {
  const [remarks, setRemarks] = useState("");
  const [error, setError] = useState("");

  // Reset form each time modal opens
  useEffect(() => {
    if (open) {
      setRemarks("");
      setError("");
    }
  }, [open]);

  if (!open || !item) return null;

  const handleSubmit = () => {
    const trimmed = remarks.trim();
    if (trimmed.length < 5) {
      setError("Rejection reason must be at least 5 characters.");
      return;
    }
    onConfirm(trimmed);
  };

  return (
    // Backdrop overlay
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.55)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1000,
        padding: "16px",
      }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="reject-modal-title"
      onClick={(e) => { if (e.target === e.currentTarget) onCancel(); }}
    >
      <div
        style={{
          background: "var(--surface-card)",
          borderRadius: "16px",
          padding: "32px",
          maxWidth: "480px",
          width: "100%",
          boxShadow: "0 20px 60px rgba(0,0,0,0.3)",
          border: "1px solid var(--line-subtle)",
        }}
      >
        {/* Header */}
        <div style={{ display: "flex", alignItems: "flex-start", gap: "12px", marginBottom: "20px" }}>
          <span style={{ fontSize: "2rem" }}>❌</span>
          <div>
            <h2 id="reject-modal-title" style={{ margin: 0, fontSize: "1.125rem", fontWeight: 700, color: "var(--ink-primary)" }}>
              Reject Leave Request
            </h2>
            <p style={{ margin: "4px 0 0", fontSize: "0.875rem", color: "var(--ink-muted)" }}>
              Rejecting{" "}
              <strong>{item.userName}</strong>'s{" "}
              <strong>{label(item.leaveType)} leave</strong> ({item.fromDate} → {item.toDate})
            </p>
          </div>
        </div>

        {/* Reason Input */}
        <div className="form-group">
          <label htmlFor="reject-reason" className="form-label">
            Rejection Reason <span style={{ color: "#ef4444" }}>*</span>
          </label>
          <textarea
            id="reject-reason"
            className={`form-input ${error ? "is-invalid" : ""}`}
            rows={4}
            value={remarks}
            onChange={(e) => {
              setRemarks(e.target.value);
              if (error) setError("");
            }}
            placeholder="Explain why this leave request is being rejected (min 5 characters)..."
            disabled={busy}
            aria-invalid={!!error}
            aria-describedby={error ? "reject-reason-error" : undefined}
            style={{ resize: "vertical", fontFamily: "inherit" }}
            autoFocus
            maxLength={500}
          />
          <div style={{ display: "flex", justifyContent: "space-between", marginTop: "4px" }}>
            {error ? (
              <div id="reject-reason-error" className="field-error" role="alert">{error}</div>
            ) : (
              <div />
            )}
            <span style={{ fontSize: "0.75rem", color: "var(--ink-muted)" }}>
              {remarks.length}/500
            </span>
          </div>
        </div>

        {/* Action Buttons */}
        <div style={{ display: "flex", gap: "12px", justifyContent: "flex-end", marginTop: "20px" }}>
          <button
            className="btn btn-secondary btn-sm"
            onClick={onCancel}
            disabled={busy}
          >
            Cancel
          </button>
          <button
            className="btn btn-danger btn-sm"
            onClick={handleSubmit}
            disabled={busy}
          >
            {busy ? "⏳ Rejecting..." : "✖ Confirm Rejection"}
          </button>
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// Main Dashboard Component
// =============================================================================
/**
 * Modern Commercial-Grade Dashboard for Settribe Leave Management.
 *
 * Features:
 * - Personal and admin leave views with search, filter, and sort
 * - Rejection modal with mandatory reason capture
 * - Admin remarks displayed prominently on rejected cards
 * - Employee profile metadata (department, designation, ID) in nav and leave cards
 * - Dark/light theme toggle
 * - KPI summary cards
 */
export default function Dashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user?.role === "ADMIN";

  // Tab state: "mine" = personal leaves, "all" = organization requests (Admin only)
  const [tab, setTab] = useState("mine");

  // Filter dropdown state
  const [filters, setFilters] = useState({ status: "", type: "" });
  // Client-side search keyword
  const [searchQuery, setSearchQuery] = useState("");
  // Sort order: "newest" | "oldest" | "date"
  const [sortBy, setSortBy] = useState("newest");

  // Core data states
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  // Modals state
  const [form, setForm] = useState(null); // null = closed, {} = new, obj = edit
  const [confirmCancel, setConfirmCancel] = useState(null); // item to cancel
  const [rejectTarget, setRejectTarget] = useState(null); // item pending admin rejection
  const [busy, setBusy] = useState(false);

  // Theme toggle state
  const [darkMode, setDarkMode] = useState(() => {
    return localStorage.getItem("theme") === "dark";
  });

  // Apply dark mode theme attribute to root html
  useEffect(() => {
    if (darkMode) {
      document.documentElement.setAttribute("data-theme", "dark");
      localStorage.setItem("theme", "dark");
    } else {
      document.documentElement.setAttribute("data-theme", "light");
      localStorage.setItem("theme", "light");
    }
  }, [darkMode]);

  const base = tab === "all" ? "/admin/leaves" : "/leaves";

  /**
   * Fetches leave records from the API applying the active query filters.
   */
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const params = {};
      if (filters.status) params.status = filters.status;
      if (filters.type) params.type = filters.type;

      const { data } = await api.get(base, { params });
      setItems(data);
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setLoading(false);
    }
  }, [base, filters]);

  useEffect(() => {
    load();
  }, [load]);

  /**
   * Higher-order async action runner with busy locking and feedback
   */
  const run = async (fn, okMsg) => {
    setBusy(true);
    setNotice("");
    try {
      await fn();
      setNotice(okMsg);
      setForm(null);
      setConfirmCancel(null);
      setRejectTarget(null);
      await load();
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setBusy(false);
    }
  };

  const handleSave = (data) =>
    run(
      () => (form.id ? api.put(`/leaves/${form.id}`, data) : api.post("/leaves", data)),
      form.id ? "Leave request updated successfully." : "Leave request submitted successfully."
    );

  const handleCancelConfirm = () => {
    if (!confirmCancel) return;
    run(() => api.delete(`/leaves/${confirmCancel.id}`), "Leave request cancelled.");
  };

  /** Admin approve — no reason required */
  const handleApprove = (l) =>
    run(
      () => api.put(`/admin/leaves/${l.id}/status`, { status: "APPROVED" }),
      `✅ Request #${l.id} has been approved.`
    );

  /** Admin reject — opens RejectModal first to capture mandatory reason */
  const handleRejectConfirm = (remarks) => {
    if (!rejectTarget) return;
    run(
      () =>
        api.put(`/admin/leaves/${rejectTarget.id}/status`, {
          status: "REJECTED",
          adminRemarks: remarks,
        }),
      `❌ Request #${rejectTarget.id} has been rejected.`
    );
  };

  /**
   * Logout handler — navigates to /login with a sign-out confirmation notice
   * that the AuthPage will display in a green banner.
   */
  const handleLogout = () => {
    logout(navigate);
  };

  const switchTab = (t) => {
    setTab(t);
    setFilters({ status: "", type: "" });
    setSearchQuery("");
    setForm(null);
    setNotice("");
  };

  // Filter and sort items locally
  const processedItems = useMemo(() => {
    let result = [...items];

    // Search query filter (matches applicant name, email, reason, or department)
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      result = result.filter(
        (item) =>
          (item.reason && item.reason.toLowerCase().includes(q)) ||
          (item.userName && item.userName.toLowerCase().includes(q)) ||
          (item.userEmail && item.userEmail.toLowerCase().includes(q)) ||
          (item.leaveType && item.leaveType.toLowerCase().includes(q)) ||
          (item.userDepartment && item.userDepartment.toLowerCase().includes(q))
      );
    }

    // Sort order
    if (sortBy === "oldest") {
      result.sort((a, b) => (a.id > b.id ? 1 : -1));
    } else if (sortBy === "date") {
      result.sort((a, b) => new Date(a.fromDate) - new Date(b.fromDate));
    } else {
      // newest
      result.sort((a, b) => (a.id < b.id ? 1 : -1));
    }

    return result;
  }, [items, searchQuery, sortBy]);

  // Calculate dynamic metrics
  const metrics = useMemo(() => {
    const total = items.length;
    const pending = items.filter((i) => i.status === "PENDING").length;
    const approved = items.filter((i) => i.status === "APPROVED").length;
    const rejected = items.filter((i) => i.status === "REJECTED").length;
    return { total, pending, approved, rejected };
  }, [items]);

  return (
    <div className="dashboard-page" style={{ minHeight: "100vh", display: "flex", flexDirection: "column" }}>
      {/* --------------------------------------------------------------------
         Top Navigation Bar
         -------------------------------------------------------------------- */}
      <nav className="site-nav" aria-label="Dashboard Navigation">
        <div className="nav-container">
          <Link to="/" className="nav-brand" aria-label="Settribe Home">
            <SettribeLogo height={36} />
          </Link>

          <div className="nav-actions">
            {/* Dark/Light Mode Switcher */}
            <button
              className="btn btn-ghost btn-sm"
              onClick={() => setDarkMode(!darkMode)}
              aria-label={darkMode ? "Switch to light mode" : "Switch to dark mode"}
              title={darkMode ? "Light mode" : "Dark mode"}
            >
              {darkMode ? "☀️ Light" : "🌙 Dark"}
            </button>

            {/* User Greeting & Role Badge with Profile Info */}
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <div style={{ textAlign: "right" }}>
                <div style={{ fontSize: "0.875rem", fontWeight: 600, color: "var(--ink-primary)" }}>
                  {user?.name}
                </div>
                {/* Show department + employee ID as subtitle if available */}
                {(user?.department || user?.employeeId) && (
                  <div style={{ fontSize: "0.7rem", color: "var(--ink-muted)", lineHeight: 1.3 }}>
                    {user?.employeeId && <span>{user.employeeId}</span>}
                    {user?.employeeId && user?.department && " · "}
                    {user?.department && <span>{user.department}</span>}
                  </div>
                )}
              </div>
              <span className={`badge badge-role ${isAdmin ? "admin" : ""}`}>
                {label(user?.role)}
              </span>
            </div>

            <button onClick={handleLogout} className="btn btn-secondary btn-sm" aria-label="Sign out">
              Sign out
            </button>
          </div>
        </div>
      </nav>

      {/* --------------------------------------------------------------------
         Main Content Area
         -------------------------------------------------------------------- */}
      <main className="dashboard-container">
        {/* Alerts / Feedback Banners */}
        {notice && (
          <div className="banner ok" role="status">
            <span>✅</span>
            <div style={{ flex: 1 }}>{notice}</div>
            <button
              onClick={() => setNotice("")}
              style={{ background: "transparent", border: "none", cursor: "pointer" }}
            >
              ✕
            </button>
          </div>
        )}

        {error && (
          <div className="banner error" role="alert">
            <span>⚠️</span>
            <div style={{ flex: 1 }}>{error}</div>
            <button className="btn btn-sm btn-secondary" onClick={load}>
              Retry
            </button>
          </div>
        )}

        {/* Dashboard Title & Actions Header */}
        <header className="dashboard-header">
          <div className="dashboard-title-area">
            <h1>{tab === "all" ? "Organization Leave Management" : "My Leave Dashboard"}</h1>
            <p className="muted">
              {tab === "all"
                ? "Review, evaluate, and manage company-wide employee time-off applications."
                : "Submit new leave applications and track request statuses in real time."}
            </p>
            {/* Show designation if available on personal dashboard */}
            {tab === "mine" && user?.designation && (
              <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", marginTop: "2px" }}>
                🏷️ {user.designation}
                {user?.phoneNumber && ` · 📞 ${user.phoneNumber}`}
              </p>
            )}
          </div>

          {tab === "mine" && (
            <button
              className="btn btn-accent"
              onClick={() => {
                setNotice("");
                setForm({});
              }}
            >
              + Apply for Leave
            </button>
          )}
        </header>

        {/* ------------------------------------------------------------------
           KPI Summary Cards
           ------------------------------------------------------------------ */}
        <section className="metrics-grid" aria-label="Summary Statistics">
          <div className="metric-card">
            <div className="metric-header">
              <span className="metric-title">Total Requests</span>
              <span className="metric-icon">📑</span>
            </div>
            <div className="metric-value">{metrics.total}</div>
            <span className="metric-subtitle">All-time applications</span>
          </div>

          <div className="metric-card">
            <div className="metric-header">
              <span className="metric-title">Pending Review</span>
              <span className="metric-icon">⏳</span>
            </div>
            <div className="metric-value" style={{ color: "var(--status-pending-ink)" }}>
              {metrics.pending}
            </div>
            <span className="metric-subtitle">Awaiting approval</span>
          </div>

          <div className="metric-card">
            <div className="metric-header">
              <span className="metric-title">Approved</span>
              <span className="metric-icon">✅</span>
            </div>
            <div className="metric-value" style={{ color: "var(--status-approved-ink)" }}>
              {metrics.approved}
            </div>
            <span className="metric-subtitle">Granted time off</span>
          </div>

          <div className="metric-card">
            <div className="metric-header">
              <span className="metric-title">Rejected / Cancelled</span>
              <span className="metric-icon">❌</span>
            </div>
            <div className="metric-value" style={{ color: "var(--status-rejected-ink)" }}>
              {metrics.rejected}
            </div>
            <span className="metric-subtitle">Declined or withdrawn</span>
          </div>
        </section>

        {/* ------------------------------------------------------------------
           Admin Tabs Navigation
           ------------------------------------------------------------------ */}
        {isAdmin && (
          <nav className="tabs-bar" aria-label="View selection">
            <button
              className={`tab-btn ${tab === "mine" ? "is-active" : ""}`}
              onClick={() => switchTab("mine")}
            >
              My Personal Leave
            </button>
            <button
              className={`tab-btn ${tab === "all" ? "is-active" : ""}`}
              onClick={() => switchTab("all")}
            >
              All Organization Requests ({metrics.pending} Pending)
            </button>
          </nav>
        )}

        {/* ------------------------------------------------------------------
           Search, Filters & Sorting Toolbar
           ------------------------------------------------------------------ */}
        <div className="toolbar">
          <div className="search-input-wrap">
            <span className="search-icon">🔍</span>
            <input
              type="text"
              className="form-input"
              placeholder={tab === "all" ? "Search by employee, department, or reason..." : "Search your requests..."}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Search leave requests"
            />
          </div>

          <div className="toolbar-filters">
            <select
              aria-label="Filter by status"
              className="form-select"
              value={filters.status}
              onChange={(e) => setFilters({ ...filters, status: e.target.value })}
            >
              <option value="">All Statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {label(s)}
                </option>
              ))}
            </select>

            <select
              aria-label="Filter by leave type"
              className="form-select"
              value={filters.type}
              onChange={(e) => setFilters({ ...filters, type: e.target.value })}
            >
              <option value="">All Categories</option>
              {TYPES.map((t) => (
                <option key={t} value={t}>
                  {label(t)} Leave
                </option>
              ))}
            </select>

            <select
              aria-label="Sort order"
              className="form-select"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
            >
              <option value="newest">Newest First</option>
              <option value="oldest">Oldest First</option>
              <option value="date">By Start Date</option>
            </select>
          </div>
        </div>

        {/* ------------------------------------------------------------------
           Requests List / States
           ------------------------------------------------------------------ */}
        {loading ? (
          <div aria-busy="true" aria-label="Loading leave requests">
            {[1, 2, 3].map((n) => (
              <div key={n} className="skeleton-card">
                <div className="skeleton-line short" />
                <div className="skeleton-line mid" />
                <div className="skeleton-line long" />
              </div>
            ))}
          </div>
        ) : processedItems.length === 0 && !error ? (
          <div className="empty-box">
            <div className="empty-box-icon">📋</div>
            <h3>No Leave Requests Found</h3>
            <p>
              {searchQuery || filters.status || filters.type
                ? "No applications match your active search or filter criteria."
                : tab === "all"
                ? "There are currently no leave requests in the organization."
                : "You haven't submitted any leave requests yet."}
            </p>
            <div style={{ marginTop: "16px", display: "flex", justifyContent: "center", gap: "10px" }}>
              {(searchQuery || filters.status || filters.type) && (
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={() => {
                    setFilters({ status: "", type: "" });
                    setSearchQuery("");
                  }}
                >
                  Clear Filters
                </button>
              )}
              {tab === "mine" && (
                <button className="btn btn-accent btn-sm" onClick={() => setForm({})}>
                  + Apply for Leave
                </button>
              )}
            </div>
          </div>
        ) : (
          <section className="requests-list" aria-label="Leave Requests">
            {processedItems.map((l) => {
              const days = calculateDays(l.fromDate, l.toDate);
              const typeIcon = getLeaveTypeIcon(l.leaveType);
              const statusIcon = getStatusIcon(l.status);

              return (
                <article key={l.id} className="request-card">
                  <div className="request-main">
                    <div className="request-title-row">
                      <span style={{ fontSize: "1.25rem" }}>{typeIcon}</span>
                      <span className="request-type-name">{label(l.leaveType)} Leave</span>

                      {/* Status Badge */}
                      <span
                        className={`badge ${
                          l.status === "PENDING"
                            ? "badge-pending"
                            : l.status === "APPROVED"
                            ? "badge-approved"
                            : "badge-rejected"
                        }`}
                      >
                        {statusIcon} {label(l.status)}
                      </span>

                      {/* Duration Chip */}
                      <span className="chip">
                        {days} {days === 1 ? "Day" : "Days"}
                      </span>
                    </div>

                    <div className="request-date-row">
                      <span>
                        📅 <strong>{l.fromDate}</strong> to <strong>{l.toDate}</strong>
                      </span>
                      {/* Admin view: show employee profile info */}
                      {tab === "all" && l.userName && (
                        <span style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                          <span>
                            👤 <strong>{l.userName}</strong> ({l.userEmail})
                          </span>
                          {(l.userDepartment || l.userDesignation || l.userEmployeeId) && (
                            <span style={{ fontSize: "0.75rem", color: "var(--ink-muted)" }}>
                              {l.userEmployeeId && <span>🪪 {l.userEmployeeId}</span>}
                              {l.userEmployeeId && l.userDepartment && " · "}
                              {l.userDepartment && <span>🏢 {l.userDepartment}</span>}
                              {l.userDesignation && (
                                <span> · {l.userDesignation}</span>
                              )}
                            </span>
                          )}
                        </span>
                      )}
                    </div>

                    <p className="request-reason">{l.reason}</p>

                    {/* Admin Remarks — Shown prominently when the leave was rejected or approved with notes */}
                    {l.adminRemarks && (
                      <div
                        style={{
                          marginTop: "10px",
                          padding: "10px 14px",
                          borderRadius: "8px",
                          background:
                            l.status === "REJECTED"
                              ? "rgba(239,68,68,0.08)"
                              : "rgba(16,185,129,0.08)",
                          borderLeft: `3px solid ${
                            l.status === "REJECTED" ? "#ef4444" : "#10b981"
                          }`,
                          fontSize: "0.8125rem",
                        }}
                      >
                        <strong style={{ color: l.status === "REJECTED" ? "#ef4444" : "#10b981" }}>
                          {l.status === "REJECTED" ? "❌ Rejection Reason from Management:" : "✅ Management Note:"}
                        </strong>{" "}
                        <span style={{ color: "var(--ink-secondary)" }}>{l.adminRemarks}</span>
                      </div>
                    )}
                  </div>

                  {/* Actions Bar */}
                  <div className="request-actions">
                    {/* Personal Pending Actions */}
                    {tab === "mine" && l.status === "PENDING" && (
                      <>
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => {
                            setNotice("");
                            setForm(l);
                          }}
                        >
                          ✏️ Edit
                        </button>
                        <button
                          className="btn btn-danger-outline btn-sm"
                          disabled={busy}
                          onClick={() => setConfirmCancel(l)}
                        >
                          Cancel
                        </button>
                      </>
                    )}

                    {/* Admin Pending Actions */}
                    {tab === "all" && l.status === "PENDING" && (
                      <>
                        <button
                          className="btn btn-accent btn-sm"
                          disabled={busy}
                          onClick={() => handleApprove(l)}
                        >
                          ✔ Approve
                        </button>
                        <button
                          className="btn btn-danger btn-sm"
                          disabled={busy}
                          onClick={() => {
                            setError("");
                            setRejectTarget(l);
                          }}
                        >
                          ✖ Reject
                        </button>
                      </>
                    )}

                    {l.status !== "PENDING" && (
                      <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", textAlign: "center" }}>
                        Finalized
                      </span>
                    )}
                  </div>
                </article>
              );
            })}
          </section>
        )}

        {/* ------------------------------------------------------------------
           Leave Form Modal (Create or Edit)
           ------------------------------------------------------------------ */}
        {form && (
          <LeaveForm
            initial={form}
            busy={busy}
            onSubmit={handleSave}
            onCancel={() => setForm(null)}
          />
        )}

        {/* ------------------------------------------------------------------
           Admin Rejection Reason Modal
           ------------------------------------------------------------------ */}
        <RejectModal
          open={!!rejectTarget}
          item={rejectTarget}
          busy={busy}
          onConfirm={handleRejectConfirm}
          onCancel={() => setRejectTarget(null)}
        />

        {/* ------------------------------------------------------------------
           In-App Confirmation Modal (Cancelling Request)
           ------------------------------------------------------------------ */}
        <ConfirmModal
          open={!!confirmCancel}
          title="Cancel Leave Application"
          message={`Are you sure you want to cancel your ${confirmCancel ? label(confirmCancel.leaveType) : ""} leave request for ${confirmCancel?.fromDate} to ${confirmCancel?.toDate}? This action cannot be undone.`}
          confirmText="Yes, Cancel Request"
          cancelText="Keep Request"
          isDestructive={true}
          onConfirm={handleCancelConfirm}
          onCancel={() => setConfirmCancel(null)}
        />
      </main>

      {/* Footer */}
      <footer className="site-footer" style={{ marginTop: "auto", padding: "24px" }}>
        <div className="footer-container" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "12px", fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
          <span>© 2026 SETTribe LLP — Employee Leave Management System</span>
          <span>
            Logged in as <strong>{user?.email}</strong>
            {user?.employeeId && <span style={{ marginLeft: "8px", opacity: 0.7 }}>({user.employeeId})</span>}
          </span>
        </div>
      </footer>
    </div>
  );
}
