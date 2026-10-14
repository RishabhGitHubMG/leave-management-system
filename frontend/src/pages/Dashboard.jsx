import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  Search, X, CalendarDays, Clock, CheckCircle2, XCircle,
  Filter, ChevronDown, AlertTriangle, Info, RefreshCw,
  Pencil, Trash2, ThumbsUp, ThumbsDown, Download, CalendarPlus,
  SlidersHorizontal, ArrowUpDown, User, Building2, BadgeCheck, ClipboardList,
} from "lucide-react";

import api, { errMsg } from "../api";
import { useAuth } from "../auth";
import SettribeLogo from "../components/SettribeLogo";
import LeaveForm, { TYPES, label } from "../components/LeaveForm";
import ConfirmModal from "../components/ConfirmModal";
import AvatarDropdown from "../components/AvatarDropdown";
import LeaveBalanceWidget from "../components/LeaveBalanceWidget";
import ProfileForm from "../components/ProfileForm";

/* ──────────────────────────────────────────────────────────────────────────
   Utility Helpers
   ────────────────────────────────────────────────────────────────────────── */

/** Format ISO date string → "Oct 12, 2026" */
function fmtDate(iso) {
  if (!iso) return "—";
  try {
    return new Date(iso + "T00:00:00").toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  } catch {
    return iso;
  }
}

/** Calendar day count inclusive */
function calcDays(from, to) {
  if (!from || !to) return 1;
  const diff = new Date(to) - new Date(from);
  if (diff < 0) return 1;
  return Math.round(diff / 86400000) + 1;
}

/* ──────────────────────────────────────────────────────────────────────────
   Sub-Components
   ────────────────────────────────────────────────────────────────────────── */

/**
 * StatusBadge — semantic colored pill for leave status
 */
function StatusBadge({ status }) {
  const cfg = {
    PENDING:  { cls: "badge-pending",  Icon: Clock,         text: "Pending" },
    APPROVED: { cls: "badge-approved", Icon: CheckCircle2,  text: "Approved" },
    REJECTED: { cls: "badge-rejected", Icon: XCircle,       text: "Rejected" },
  }[status] ?? { cls: "", Icon: Info, text: status };

  return (
    <span className={`badge ${cfg.cls}`}>
      <cfg.Icon size={11} />
      {cfg.text}
    </span>
  );
}

/**
 * LeaveTypeChip — pill showing leave category with icon
 */
function LeaveTypeChip({ type }) {
  const cfg = {
    SICK:    { emoji: "🏥", color: "#0891b2" },
    ANNUAL:  { emoji: "🏖️", color: "#16a34a" },
    CASUAL:  { emoji: "🌴", color: "#7c3aed" },
    UNPAID:  { emoji: "📋", color: "#b45309" },
  }[type] ?? { emoji: "🗓️", color: "#64748b" };

  return (
    <span className="chip" style={{ color: cfg.color }}>
      {cfg.emoji} {label(type)} Leave
    </span>
  );
}

/**
 * AdminRemarksCallout — styled rejection / approval note from management
 */
function AdminRemarksCallout({ status, remarks, onReApply }) {
  const isRejected = status === "REJECTED";
  return (
    <div className={`remarks-callout ${isRejected ? "rejected" : "approved"}`}>
      <span className="remarks-icon">
        {isRejected
          ? <AlertTriangle size={15} />
          : <CheckCircle2 size={15} />
        }
      </span>
      <div className="remarks-body">
        <span className="remarks-label">
          {isRejected ? "Rejection reason from management:" : "Management note:"}
        </span>
        <span>{remarks}</span>
      </div>
      {isRejected && onReApply && (
        <button
          className="btn btn-xs btn-secondary"
          onClick={onReApply}
          style={{ flexShrink: 0, alignSelf: "flex-start" }}
        >
          Re-apply
        </button>
      )}
    </div>
  );
}

function ApproveModal({ open, item, busy, actionError, onConfirm, onCancel }) {
  const [remarks, setRemarks] = useState("");

  useEffect(() => {
    if (open) setRemarks("");
  }, [open]);

  if (!open || !item) return null;

  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="approve-modal-title"
      onClick={(event) => { if (event.target === event.currentTarget && !busy) onCancel(); }}
    >
      <div className="modal-dialog" style={{ maxWidth: 480 }}>
        <div className="modal-header">
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <CheckCircle2 size={20} color="#16a34a" />
            <h2 id="approve-modal-title">Approve Leave Request</h2>
          </div>
          <button className="modal-close-btn" onClick={onCancel} disabled={busy} aria-label="Close"><X size={16} /></button>
        </div>
        <div className="modal-body">
          {actionError && <div className="banner error" role="alert">{actionError}</div>}
          <p style={{ marginBottom: 16, color: "var(--ink-secondary)" }}>
            Approving <strong>{item.userName}</strong>'s {label(item.leaveType)} Leave ({fmtDate(item.fromDate)} → {fmtDate(item.toDate)}).
          </p>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label htmlFor="approve-remarks" className="form-label">Approval remarks <span className="muted">(optional)</span></label>
            <textarea
              id="approve-remarks"
              className="form-textarea"
              rows={4}
              maxLength={500}
              value={remarks}
              onChange={(event) => setRemarks(event.target.value)}
              placeholder="Add a note for the employee, if needed..."
              disabled={busy}
            />
            <div className="char-counter" style={{ textAlign: "right" }}>{remarks.length}/500</div>
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary btn-sm" onClick={onCancel} disabled={busy}>Cancel</button>
          <button className="btn btn-accent btn-sm" onClick={() => onConfirm(remarks.trim())} disabled={busy}>
            <ThumbsUp size={14} /> {busy ? "Approving…" : "Confirm Approval"}
          </button>
        </div>
      </div>
    </div>
  );
}

function RejectModal({ open, item, busy, actionError, onConfirm, onCancel }) {
  const [remarks, setRemarks] = useState("");
  const [error, setError] = useState("");
  const textareaRef = useRef(null);

  useEffect(() => {
    if (open) {
      setRemarks("");
      setError("");
      setTimeout(() => textareaRef.current?.focus(), 50);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handler = (e) => { if (e.key === "Escape") onCancel(); };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [open, onCancel]);

  if (!open || !item) return null;

  const submit = () => {
    const trimmed = remarks.trim();
    if (trimmed.length < 5) {
      setError("Reason must be at least 5 characters.");
      return;
    }
    onConfirm(trimmed);
  };

  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reject-modal-title"
      onClick={(e) => { if (e.target === e.currentTarget) onCancel(); }}
    >
      <div className="modal-dialog" style={{ maxWidth: 480 }}>
        <div className="modal-header">
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <XCircle size={20} color="#dc2626" />
            <h2 id="reject-modal-title">Reject Leave Request</h2>
          </div>
          <button className="modal-close-btn" onClick={onCancel} aria-label="Close"><X size={16} /></button>
        </div>

        <div className="modal-body">
          {actionError && <div className="banner error" role="alert">{actionError}</div>}
          {/* Context summary */}
          <div
            style={{
              padding: "10px 14px",
              borderRadius: 8,
              background: "var(--bg-surface-muted)",
              border: "1px solid var(--line-subtle)",
              fontSize: "0.875rem",
              color: "var(--ink-secondary)",
              marginBottom: 16,
            }}
          >
            Rejecting <strong>{item.userName}</strong>'s{" "}
            <strong>{label(item.leaveType)} Leave</strong>
            {" "}({fmtDate(item.fromDate)} → {fmtDate(item.toDate)})
          </div>

          <div className="form-group" style={{ marginBottom: 0 }}>
            <label htmlFor="reject-reason" className="form-label">
              Rejection Reason <span style={{ color: "#ef4444" }}>*</span>
            </label>
            <textarea
              id="reject-reason"
              ref={textareaRef}
              className={`form-textarea ${error ? "is-invalid" : ""}`}
              rows={4}
              value={remarks}
              onChange={(e) => { setRemarks(e.target.value); if (error) setError(""); }}
              placeholder="Explain why this request is being rejected (min 5 characters)..."
              disabled={busy}
              maxLength={500}
              aria-invalid={!!error}
              aria-describedby={error ? "reject-reason-err" : undefined}
            />
            <div style={{ display: "flex", justifyContent: "space-between", marginTop: 4 }}>
              {error
                ? <div id="reject-reason-err" className="field-error" role="alert">{error}</div>
                : <div />
              }
              <span className="char-counter">{remarks.length}/500</span>
            </div>
          </div>
        </div>

        <div className="modal-footer">
          <button className="btn btn-secondary btn-sm" onClick={onCancel} disabled={busy}>Cancel</button>
          <button className="btn btn-danger btn-sm" onClick={submit} disabled={busy}>
            <XCircle size={14} />
            {busy ? "Rejecting…" : "Confirm Rejection"}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * FilterPills — compact pill buttons for quick status/type filtering
 * Shows "All" + one pill per option; active pill uses brand green.
 */
function FilterPills({ label: groupLabel, options, value, onChange }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap" }}>
      <button
        className={`filter-pill ${value === "" ? "active" : ""}`}
        onClick={() => onChange("")}
      >
        All {groupLabel}
      </button>
      {options.map((opt) => (
        <button
          key={opt.value}
          className={`filter-pill ${value === opt.value ? "active" : ""}`}
          onClick={() => onChange(opt.value)}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────────────────
   Main Dashboard
   ────────────────────────────────────────────────────────────────────────── */

const STATUS_OPTIONS = [
  { value: "PENDING",  label: "Pending"  },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
];

const TYPE_OPTIONS = TYPES.map((t) => ({ value: t, label: `${label(t)} Leave` }));

const SORT_OPTIONS = [
  { value: "newest", label: "Newest First" },
  { value: "oldest", label: "Oldest First" },
  { value: "date",   label: "By Start Date" },
];

/**
 * Dashboard — Primary view for both employees and admins.
 */
export default function Dashboard() {
  const { user, logout, syncUserProfile } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user?.role === "ADMIN";
  const isReviewer = ["ADMIN", "HR", "MANAGER"].includes(user?.role);

  /* ── State ── */
  const [tab, setTab] = useState("mine");
  const [statusFilter, setStatusFilter] = useState("");
  const [typeFilter, setTypeFilter]     = useState("");
  const [searchQuery, setSearchQuery]   = useState("");
  const [sortBy, setSortBy]             = useState("newest");
  const [showFilters, setShowFilters]   = useState(false);

  const [items, setItems]   = useState([]);
  const [balanceItems, setBalanceItems] = useState([]);
  const latestLoad = useRef(0);
  const [loading, setLoading] = useState(true);
  const [error, setError]   = useState("");
  const [modalError, setModalError] = useState("");
  const [notice, setNotice] = useState("");

  const [form, setForm]               = useState(null);
  const [confirmCancel, setConfirmCancel] = useState(null);
  const [approveTarget, setApproveTarget] = useState(null);
  const [rejectTarget, setRejectTarget]  = useState(null);
  const [showProfileForm, setShowProfileForm] = useState(false);
  const [busy, setBusy]               = useState(false);

  const [darkMode, setDarkMode] = useState(
    () => localStorage.getItem("theme") === "dark"
  );

  /* ── Dark mode sync ── */
  useEffect(() => {
    document.documentElement.setAttribute("data-theme", darkMode ? "dark" : "light");
    localStorage.setItem("theme", darkMode ? "dark" : "light");
  }, [darkMode]);

  const base = tab === "all" ? "/admin/leaves" : "/leaves";

  /* ── Data fetching ── */
  const load = useCallback(async () => {
    const loadId = ++latestLoad.current;
    setLoading(true);
    setError("");
    try {
      const params = {};
      if (statusFilter) params.status = statusFilter;
      if (typeFilter)   params.type   = typeFilter;
      const { data } = await api.get(base, { params });
      if (loadId === latestLoad.current) setItems(data);
      if (base === "/leaves") {
        const balanceData = statusFilter || typeFilter
          ? (await api.get("/leaves")).data
          : data;
        if (loadId === latestLoad.current) setBalanceItems(balanceData);
      }
    } catch (e) {
      if (loadId === latestLoad.current) setError(errMsg(e));
    } finally {
      if (loadId === latestLoad.current) setLoading(false);
    }
  }, [base, statusFilter, typeFilter]);

  useEffect(() => { load(); }, [load]);

  /* ── Action runner ── */
  const run = async (fn, msg) => {
    setBusy(true);
    setNotice("");
    setError("");
    setModalError("");
    try {
      await fn();
      setNotice(msg);
      setModalError("");
      setForm(null);
      setConfirmCancel(null);
      setApproveTarget(null);
      setRejectTarget(null);
      await load();
    } catch (e) {
      const message = errMsg(e);
      setError(message);
      setModalError(message);
    } finally {
      setBusy(false);
    }
  };

  const handleSave = (data) =>
    run(
      () => form.id ? api.put(`/leaves/${form.id}`, data) : api.post("/leaves", data),
      form.id ? "Leave request updated successfully." : "Leave request submitted."
    );

  const handleCancelConfirm = () =>
    run(() => api.delete(`/leaves/${confirmCancel.id}`), "Leave request cancelled.");

  const handleApprove = (remarks) =>
    run(
      () => api.put(`/admin/leaves/${approveTarget.id}/status`, { status: "APPROVED", adminRemarks: remarks || null }),
      `Request approved for ${approveTarget.userName}.`
    );

  const handleRejectConfirm = (remarks) =>
    run(
      () => api.put(`/admin/leaves/${rejectTarget.id}/status`, { status: "REJECTED", adminRemarks: remarks }),
      `Request rejected.`
    );

  const handleLogout = () => logout(navigate);

  const switchTab = (t) => {
    setTab(t);
    setStatusFilter("");
    setTypeFilter("");
    setSearchQuery("");
    setNotice("");
    setShowFilters(false);
  };

  /* ── Processed items (search + sort client-side) ── */
  const processedItems = useMemo(() => {
    let res = [...items];
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      res = res.filter((i) =>
        [i.reason, i.userName, i.userEmail, i.leaveType, i.userDepartment]
          .some((v) => v?.toLowerCase().includes(q))
      );
    }
    if (sortBy === "oldest") res.sort((a, b) => a.id - b.id);
    else if (sortBy === "date") res.sort((a, b) => new Date(a.fromDate) - new Date(b.fromDate));
    else res.sort((a, b) => b.id - a.id);
    return res;
  }, [items, searchQuery, sortBy]);

  /* ── KPI metrics ── */
  const metrics = useMemo(() => ({
    total:    items.length,
    pending:  items.filter((i) => i.status === "PENDING").length,
    approved: items.filter((i) => i.status === "APPROVED").length,
    rejected: items.filter((i) => i.status === "REJECTED").length,
  }), [items]);

  const activeFilterCount = [statusFilter, typeFilter].filter(Boolean).length;

  /* ── Render ── */
  return (
    <div className="dashboard-page">

      {/* ================================================================
          Navigation Bar
          ================================================================ */}
      <nav className="site-nav" aria-label="Main navigation">
        <div className="nav-container">
          {/* Brand */}
          <Link to="/" className="nav-brand" aria-label="Go to Settribe home">
            <SettribeLogo height={34} />
          </Link>

          {/* Right side: avatar dropdown only */}
          <div className="nav-actions">
            <AvatarDropdown
              user={user}
              darkMode={darkMode}
              onToggleDark={() => setDarkMode((d) => !d)}
              onEditProfile={() => setShowProfileForm(true)}
              onLogout={handleLogout}
            />
          </div>
        </div>
      </nav>

      {/* ================================================================
          Main Content
          ================================================================ */}
      <main className="dashboard-container">

        {/* Feedback Banners */}
        {notice && (
          <div className="banner ok" role="status" aria-live="polite">
            <CheckCircle2 size={16} />
            <div style={{ flex: 1 }}>{notice}</div>
            <button
              onClick={() => setNotice("")}
              aria-label="Dismiss"
              style={{ background: "none", border: "none", cursor: "pointer", color: "inherit" }}
            >
              <X size={14} />
            </button>
          </div>
        )}
        {error && (
          <div className="banner error" role="alert" aria-live="assertive">
            <AlertTriangle size={16} />
            <div style={{ flex: 1 }}>{error}</div>
            <button className="btn btn-sm btn-secondary" onClick={load}>
              <RefreshCw size={12} /> Retry
            </button>
          </div>
        )}

        {/* Page Header */}
        <header className="dashboard-header">
          <div className="dashboard-title-area">
            <h1>
              {tab === "all"
                ? (user?.role === "MANAGER" ? "Team Leave Management" : "Organization Leave Management")
                : "My Leave Dashboard"}
            </h1>
            <p className="muted">
              {tab === "all"
                ? (user?.role === "MANAGER"
                    ? "Review time-off applications from your direct reports."
                    : "Review and manage company-wide employee time-off applications.")
                : "Submit new applications and track your request statuses."}
            </p>
          </div>

          {tab === "mine" && (
            <button
              className="btn btn-accent"
              onClick={() => { setNotice(""); setModalError(""); setForm({}); }}
              aria-label="Apply for leave"
            >
              <CalendarPlus size={15} />
              Apply for Leave
            </button>
          )}
          {isAdmin && (
            <button className="btn btn-secondary" onClick={() => navigate("/admin/users")}>
              Manage Users
            </button>
          )}
        </header>

        {/* ── KPI Cards ── */}
        <section className="metrics-grid" aria-label="Leave request summary">
          {[
            { label: "Total Requests",      value: metrics.total,    Icon: CalendarDays,  color: "#16a34a", bg: "rgba(22,163,74,0.08)",  sub: "All applications" },
            { label: "Pending Review",       value: metrics.pending,  Icon: Clock,         color: "#d97706", bg: "rgba(217,119,6,0.08)",   sub: "Awaiting decision" },
            { label: "Approved",             value: metrics.approved, Icon: CheckCircle2,  color: "#16a34a", bg: "rgba(22,163,74,0.08)",  sub: "Requests approved" },
            { label: "Rejected / Cancelled", value: metrics.rejected, Icon: XCircle,       color: "#dc2626", bg: "rgba(220,38,38,0.08)",  sub: "Declined" },
          ].map(({ label: lbl, value, Icon, color, bg, sub }) => (
            <div key={lbl} className="metric-card" aria-label={lbl}>
              <div className="metric-header">
                <span className="metric-title">{lbl}</span>
                <div className="metric-icon-wrap" style={{ background: bg }}>
                  <Icon size={16} color={color} />
                </div>
              </div>
              <div className="metric-value" style={{ color }}>{value}</div>
              <span className="metric-subtitle">{sub}</span>
            </div>
          ))}
        </section>

        {/* ── Leave Balance Widget (personal tab only) ── */}
        {tab === "mine" && <LeaveBalanceWidget items={balanceItems} />}

        {/* ── Admin Tabs ── */}
        {isReviewer && (
          <nav className="tabs-bar" aria-label="View selection" role="tablist">
            <button
              role="tab"
              aria-selected={tab === "mine"}
              className={`tab-btn ${tab === "mine" ? "is-active" : ""}`}
              onClick={() => switchTab("mine")}
            >
              My Leave
            </button>
            <button
              role="tab"
              aria-selected={tab === "all"}
              className={`tab-btn ${tab === "all" ? "is-active" : ""}`}
              onClick={() => switchTab("all")}
            >
              {user?.role === "MANAGER" ? "Team Requests" : "All Requests"}
              {metrics.pending > 0 && (
                <span
                  style={{
                    display: "inline-flex", alignItems: "center", justifyContent: "center",
                    minWidth: 18, height: 18, borderRadius: 9999,
                    background: "#dc2626", color: "#fff",
                    fontSize: "0.65rem", fontWeight: 800,
                    marginLeft: 6, padding: "0 4px",
                  }}
                  aria-label={`${metrics.pending} pending`}
                >
                  {metrics.pending}
                </span>
              )}
            </button>
          </nav>
        )}

        {/* ── Search + Filter Toolbar ── */}
        <div className="toolbar">
          {/* Search */}
          <div className="search-input-wrap">
            <span className="search-icon-left" aria-hidden="true">
              <Search size={15} />
            </span>
            <input
              type="text"
              className="form-input"
              placeholder={
                tab === "all"
                  ? "Search by employee, department, reason…"
                  : "Search your requests…"
              }
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Search leave requests"
            />
            {searchQuery && (
              <button
                className="search-clear-btn"
                onClick={() => setSearchQuery("")}
                aria-label="Clear search"
              >
                <X size={13} />
              </button>
            )}
          </div>

          {/* Filter toggle */}
          <button
            className={`btn btn-sm ${activeFilterCount > 0 ? "btn-accent" : "btn-secondary"}`}
            onClick={() => setShowFilters((p) => !p)}
            aria-expanded={showFilters}
            aria-label="Toggle filters"
          >
            <Filter size={13} />
            Filters
            {activeFilterCount > 0 && (
              <span
                style={{
                  background: "#fff", color: "#16a34a",
                  borderRadius: "9999px", width: 16, height: 16,
                  fontSize: "0.65rem", fontWeight: 800,
                  display: "flex", alignItems: "center", justifyContent: "center",
                }}
              >
                {activeFilterCount}
              </span>
            )}
          </button>

          {/* Sort */}
          <div style={{ position: "relative", display: "flex", alignItems: "center" }}>
            <ArrowUpDown size={13} color="var(--ink-muted)" style={{ position: "absolute", left: 10, pointerEvents: "none" }} />
            <select
              className="form-select btn-sm"
              style={{ paddingLeft: 30, paddingRight: 24, cursor: "pointer" }}
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
              aria-label="Sort order"
            >
              {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>
        </div>

        {/* Expanded filter pills */}
        {showFilters && (
          <div
            style={{
              padding: "14px 16px",
              background: "var(--bg-surface)",
              border: "1px solid var(--line-subtle)",
              borderRadius: "var(--radius-lg)",
              marginTop: -8,
              marginBottom: 20,
              display: "flex",
              flexDirection: "column",
              gap: 12,
              boxShadow: "var(--shadow-xs)",
            }}
          >
            <div>
              <p style={{ fontSize: "0.75rem", fontWeight: 700, color: "var(--ink-muted)", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 8 }}>
                Status
              </p>
              <FilterPills
                label="Statuses"
                options={STATUS_OPTIONS}
                value={statusFilter}
                onChange={setStatusFilter}
              />
            </div>
            <div>
              <p style={{ fontSize: "0.75rem", fontWeight: 700, color: "var(--ink-muted)", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 8 }}>
                Leave Type
              </p>
              <FilterPills
                label="Categories"
                options={TYPE_OPTIONS}
                value={typeFilter}
                onChange={setTypeFilter}
              />
            </div>
            {activeFilterCount > 0 && (
              <button
                className="btn btn-ghost btn-xs"
                style={{ alignSelf: "flex-start", color: "#dc2626" }}
                onClick={() => { setStatusFilter(""); setTypeFilter(""); }}
              >
                <X size={12} /> Clear all filters
              </button>
            )}
          </div>
        )}

        {/* ── Request List ── */}
        {loading ? (
          /* Skeleton */
          <div aria-busy="true" aria-label="Loading requests">
            {[1, 2, 3].map((n) => (
              <div key={n} className="skeleton-card">
                <div className="skeleton-line short" />
                <div className="skeleton-line mid" />
                <div className="skeleton-line long" />
              </div>
            ))}
          </div>
        ) : processedItems.length === 0 ? (
          /* Empty state */
          <div className="empty-box">
            <div className="empty-box-icon"><ClipboardList size={48} /></div>
            <h3>No Leave Requests Found</h3>
            <p style={{ marginBottom: 16 }}>
              {searchQuery || statusFilter || typeFilter
                ? "No results match your active search or filters."
                : tab === "all"
                ? "There are currently no employee leave requests."
                : "You haven't submitted any leave requests yet."}
            </p>
            <div style={{ display: "flex", gap: 10, justifyContent: "center", flexWrap: "wrap" }}>
              {(searchQuery || statusFilter || typeFilter) && (
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={() => { setSearchQuery(""); setStatusFilter(""); setTypeFilter(""); }}
                >
                  <X size={12} /> Clear Filters
                </button>
              )}
              {tab === "mine" && (
                <button className="btn btn-accent btn-sm" onClick={() => { setModalError(""); setForm({}); }}>
                  <CalendarPlus size={13} /> Apply for Leave
                </button>
              )}
            </div>
          </div>
        ) : (
          /* Leave cards */
          <section className="requests-list" aria-label="Leave requests">
            {processedItems.map((l) => {
              const days = calcDays(l.fromDate, l.toDate);
              const isSingle = l.fromDate === l.toDate;

              return (
                <article key={l.id} className="request-card">
                  {/* ── Left: main info ── */}
                  <div className="request-main">
                    {/* Title row */}
                    <div className="request-title-row">
                      <LeaveTypeChip type={l.leaveType} />
                      <StatusBadge status={l.status} />
                      <span className="chip">
                        {days} {days === 1 ? "Day" : "Days"}
                      </span>
                    </div>

                    {/* Date row */}
                    <div className="request-date-row">
                      <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
                        <CalendarDays size={13} color="var(--ink-muted)" />
                        {isSingle
                          ? fmtDate(l.fromDate)
                          : <>{fmtDate(l.fromDate)} <span style={{ color: "var(--ink-muted)" }}>→</span> {fmtDate(l.toDate)}</>
                        }
                        <span style={{ color: "var(--ink-muted)", fontSize: "0.75rem" }}>
                          · {days} {days === 1 ? "day" : "days"}
                        </span>
                      </span>

                      {/* Admin view: employee profile */}
                      {tab === "all" && l.userName && (
                        <span
                          style={{
                            display: "flex",
                            flexDirection: "column",
                            gap: 2,
                          }}
                        >
                          <span>
                            <User size={13} /> <strong>{l.userName}</strong>
                            <span style={{ color: "var(--ink-muted)", fontSize: "0.8rem" }}>
                              {" "}({l.userEmail})
                            </span>
                          </span>
                          {(l.userEmployeeId || l.userDepartment || l.userDesignation) && (
                            <span style={{ fontSize: "0.75rem", color: "var(--ink-muted)", display: "flex", gap: 6, flexWrap: "wrap" }}>
                              {l.userEmployeeId && <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}><BadgeCheck size={12} />{l.userEmployeeId}</span>}
                              {l.userDepartment && <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}><Building2 size={12} />{l.userDepartment}</span>}
                              {l.userDesignation && <span>· {l.userDesignation}</span>}
                            </span>
                          )}
                        </span>
                      )}
                    </div>

                    {/* Reason */}
                    <p className="request-reason">{l.reason}</p>

                    {/* Admin Remarks Callout */}
                    {l.adminRemarks && (
                      <AdminRemarksCallout
                        status={l.status}
                        remarks={l.adminRemarks}
                        onReApply={
                          tab === "mine" && l.status === "REJECTED"
                            ? () => { setModalError(""); setForm({ leaveType: l.leaveType, fromDate: l.fromDate, toDate: l.toDate, reason: "" }); }
                            : null
                        }
                      />
                    )}
                  </div>

                  {/* ── Right: contextual actions ── */}
                  <div className="request-actions">
                    {/* Personal Pending → Edit + Cancel */}
                    {tab === "mine" && l.status === "PENDING" && (
                      <>
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => { setNotice(""); setModalError(""); setForm(l); }}
                          aria-label="Edit leave request"
                        >
                          <Pencil size={12} /> Edit
                        </button>
                        <button
                          className="btn btn-danger-outline btn-sm"
                          disabled={busy}
                          onClick={() => { setModalError(""); setConfirmCancel(l); }}
                          aria-label="Cancel leave request"
                        >
                          <Trash2 size={12} /> Cancel
                        </button>
                      </>
                    )}

                    {/* Personal Approved → Export (placeholder) */}
                    {tab === "mine" && l.status === "APPROVED" && (
                      <>
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => window.print()}
                          aria-label="Export leave record"
                          title="Export / Print"
                        >
                          <Download size={12} /> Export
                        </button>
                        <button
                          className="btn btn-ghost btn-sm"
                          onClick={() => {
                            const url = `https://calendar.google.com/calendar/r/eventedit?text=${encodeURIComponent(label(l.leaveType)+" Leave")}&dates=${l.fromDate.replace(/-/g,"")}/${l.toDate.replace(/-/g,"")}`;
                            window.open(url, "_blank", "noreferrer");
                          }}
                          aria-label="Add to Google Calendar"
                          title="Add to Google Calendar"
                        >
                          <CalendarPlus size={12} /> Calendar
                        </button>
                      </>
                    )}

                    {/* Admin Pending → Approve + Reject */}
                    {tab === "all" && l.status === "PENDING" && l.userId !== user?.id && (
                      <>
                        <button
                          className="btn btn-accent btn-sm"
                          disabled={busy}
                          onClick={() => { setModalError(""); setApproveTarget(l); }}
                          aria-label={`Approve ${l.userName}'s request`}
                        >
                          <ThumbsUp size={12} /> Approve
                        </button>
                        <button
                          className="btn btn-danger btn-sm"
                          disabled={busy}
                          onClick={() => { setError(""); setModalError(""); setRejectTarget(l); }}
                          aria-label={`Reject ${l.userName}'s request`}
                        >
                          <ThumbsDown size={12} /> Reject
                        </button>
                      </>
                    )}

                    {/* Finalized state label */}
                    {tab === "all" && l.status !== "PENDING" && (
                      <span style={{ fontSize: "0.75rem", color: "var(--ink-muted)", textAlign: "center", paddingTop: 4 }}>
                        Finalized
                      </span>
                    )}
                  </div>
                </article>
              );
            })}
          </section>
        )}

        {/* Result count */}
        {!loading && processedItems.length > 0 && (
          <p style={{ marginTop: 16, fontSize: "0.8125rem", color: "var(--ink-muted)", textAlign: "center" }}>
            Showing {processedItems.length} of {items.length} request{items.length !== 1 ? "s" : ""}
          </p>
        )}
      </main>

      {showProfileForm && (
        <ProfileForm
          user={user}
          onSave={(profile) => {
            syncUserProfile(profile);
            setShowProfileForm(false);
            setNotice("Profile updated successfully.");
          }}
          onCancel={() => setShowProfileForm(false)}
        />
      )}

      {/* ── Leave Form Modal ── */}
      {form && (
        <LeaveForm
          initial={form}
          busy={busy}
          actionError={modalError}
          onSubmit={handleSave}
          onCancel={() => setForm(null)}
        />
      )}

      <ApproveModal
        open={!!approveTarget}
        item={approveTarget}
        busy={busy}
        actionError={modalError}
        onConfirm={handleApprove}
        onCancel={() => setApproveTarget(null)}
      />

      {/* ── Admin Rejection Modal ── */}
      <RejectModal
        open={!!rejectTarget}
        item={rejectTarget}
        busy={busy}
        onConfirm={handleRejectConfirm}
        actionError={modalError}
        onCancel={() => setRejectTarget(null)}
      />

      {/* ── Cancel Confirmation Modal ── */}
      <ConfirmModal
        open={!!confirmCancel}
        title="Cancel Leave Application"
        message={`Are you sure you want to withdraw your ${confirmCancel ? label(confirmCancel.leaveType) : ""} leave request for ${fmtDate(confirmCancel?.fromDate)} → ${fmtDate(confirmCancel?.toDate)}? This cannot be undone.`}
        confirmText="Yes, Cancel Request"
        cancelText="Keep Request"
        isDestructive={true}
        busy={busy}
        actionError={modalError}
        onConfirm={handleCancelConfirm}
        onCancel={() => setConfirmCancel(null)}
      />

      {/* Footer */}
      <footer className="site-footer">
        <div className="footer-container">
          <span>© {new Date().getFullYear()} SETTribe LLP · Leave Management System</span>
          <span style={{ display: "flex", alignItems: "center", gap: 8 }}>
            Signed in as <strong>{user?.email}</strong>
            {user?.employeeId && (
              <span className="chip" style={{ fontSize: "0.7rem" }}>{user.employeeId}</span>
            )}
          </span>
        </div>
      </footer>
    </div>
  );
}
