import { useCallback, useEffect, useState } from "react";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";
import LeaveForm, { TYPES, label } from "../components/LeaveForm";

/**
 * Supported leave request workflow statuses for dropdown filters.
 */
const STATUSES = ["PENDING", "APPROVED", "REJECTED"];

/**
 * Dashboard Component
 *
 * Serves as the primary operational view for authenticated users:
 * - Employees: View own leave history, apply for new leave, edit/cancel pending requests.
 * - Admins: Switch between personal leave and company-wide requests, approve or reject pending requests.
 */
export default function Dashboard() {
  // Current user credentials & role from global AuthContext
  const { user, logout } = useAuth();
  const isAdmin = user.role === "ADMIN";

  // Tab state: "mine" = current user's leaves, "all" = all organizational requests (admin only)
  const [tab, setTab] = useState("mine");

  // Filter dropdown state (filter by status e.g. PENDING, or type e.g. SICK)
  const [filters, setFilters] = useState({ status: "", type: "" });

  // List of leave items returned from backend API
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  // Modal/inline leave form state:
  // - null: form closed
  // - {}: creating a new leave request
  // - { id, leaveType, ... }: editing an existing leave request
  const [form, setForm] = useState(null);

  // Busy flag prevents duplicate API requests during save/cancel/approve actions
  const [busy, setBusy] = useState(false);

  // Compute endpoint dynamically based on current tab selection
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

  // Automatically refresh leave list when base endpoint or filter criteria changes
  useEffect(() => { load(); }, [load]);

  /**
   * Higher-order async runner: executes an action with busy locking,
   * shows success feedback on completion, resets the form, and reloads the table.
   *
   * @param {Function} fn - Async operation to execute
   * @param {string} okMsg - Success message to display to the user
   */
  const run = async (fn, okMsg) => {
    setBusy(true);
    setNotice("");
    try {
      await fn();
      setNotice(okMsg);
      setForm(null);
      await load();
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setBusy(false);
    }
  };

  /**
   * Handles submission of leave form (creates new or updates existing request).
   */
  const save = (data) =>
    run(() => (form.id ? api.put(`/leaves/${form.id}`, data) : api.post("/leaves", data)),
      form.id ? "Request updated." : "Request submitted.");

  /**
   * Cancels (deletes) a pending leave request after user confirmation.
   */
  const cancel = (l) =>
    window.confirm("Cancel this request?") && run(() => api.delete(`/leaves/${l.id}`), "Request cancelled.");

  /**
   * Admin action: approves or rejects a pending leave request.
   */
  const decide = (l, status) =>
    run(() => api.put(`/admin/leaves/${l.id}/status`, { status }), `Request ${status.toLowerCase()}.`);

  /**
   * Switches tab view between personal leaves and all leaves, resetting filters and notices.
   */
  const switchTab = (t) => {
    setTab(t);
    setFilters({ status: "", type: "" });
    setForm(null);
    setNotice("");
  };

  const filtered = filters.status || filters.type;

  return (
    <div className="shell">
      {/* Application header with user identity and sign-out button */}
      <header className="top">
        <div>
          <strong>Leave Manager</strong>
          <span className="muted"> {user.name} ({label(user.role)})</span>
        </div>
        <button onClick={logout}>Sign out</button>
      </header>

      {/* Navigation tabs for Admin users */}
      {isAdmin && (
        <nav className="tabs">
          <button className={tab === "mine" ? "on" : ""} onClick={() => switchTab("mine")}>My leave</button>
          <button className={tab === "all" ? "on" : ""} onClick={() => switchTab("all")}>All requests</button>
        </nav>
      )}

      <main>
        {/* Success notification banner */}
        {notice && <div className="banner ok" role="status">{notice}</div>}

        {/* Error notification banner with retry action */}
        {error && (
          <div className="banner error" role="alert">
            {error} <button onClick={load}>Retry</button>
          </div>
        )}

        {/* Leave application / edit form */}
        {form && <LeaveForm initial={form} busy={busy} onSubmit={save} onCancel={() => setForm(null)} />}

        {/* Filter controls and leave application trigger */}
        <div className="bar">
          <select aria-label="Filter by status" value={filters.status}
            onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
          <select aria-label="Filter by type" value={filters.type}
            onChange={(e) => setFilters({ ...filters, type: e.target.value })}>
            <option value="">All types</option>
            {TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
          </select>
          {tab === "mine" && !form && (
            <button className="primary push" onClick={() => { setNotice(""); setForm({}); }}>Apply for leave</button>
          )}
        </div>

        {/* Leave request list / states */}
        {loading ? (
          <p className="muted center">Loading requests...</p>
        ) : items.length === 0 && !error ? (
          <div className="empty">
            <p>{filtered ? "No requests match these filters." : tab === "all" ? "No leave requests yet." : "You haven't applied for any leave yet."}</p>
            {filtered && <button onClick={() => setFilters({ status: "", type: "" })}>Clear filters</button>}
          </div>
        ) : (
          <ul className="list">
            {items.map((l) => (
              <li key={l.id} className="item">
                <div>
                  <div className="title">
                    {label(l.leaveType)} leave
                    <span className={`badge ${l.status.toLowerCase()}`}>{label(l.status)}</span>
                  </div>
                  <div className="muted">{l.fromDate} to {l.toDate}</div>
                  {tab === "all" && <div className="muted">{l.userName} ({l.userEmail})</div>}
                  <p>{l.reason}</p>
                </div>
                <div className="btns">
                  {/* Actions for personal pending leaves: Edit or Cancel */}
                  {tab === "mine" && l.status === "PENDING" && (
                    <>
                      <button onClick={() => { setNotice(""); setForm(l); }}>Edit</button>
                      <button className="danger" disabled={busy} onClick={() => cancel(l)}>Cancel</button>
                    </>
                  )}
                  {/* Actions for Admin on company-wide pending leaves: Approve or Reject */}
                  {tab === "all" && l.status === "PENDING" && (
                    <>
                      <button className="primary" disabled={busy} onClick={() => decide(l, "APPROVED")}>Approve</button>
                      <button className="danger" disabled={busy} onClick={() => decide(l, "REJECTED")}>Reject</button>
                    </>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  );
}

