import { useCallback, useEffect, useState } from "react";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";
import LeaveForm, { TYPES, label } from "../components/LeaveForm";

const STATUSES = ["PENDING", "APPROVED", "REJECTED"];

export default function Dashboard() {
  const { user, logout } = useAuth();
  const isAdmin = user.role === "ADMIN";
  const [tab, setTab] = useState("mine");
  const [filters, setFilters] = useState({ status: "", type: "" });
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [form, setForm] = useState(null); // null = closed, {} = new, leave = edit
  const [busy, setBusy] = useState(false);
  const base = tab === "all" ? "/admin/leaves" : "/leaves";

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

  useEffect(() => { load(); }, [load]);

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

  const save = (data) =>
    run(() => (form.id ? api.put(`/leaves/${form.id}`, data) : api.post("/leaves", data)),
      form.id ? "Request updated." : "Request submitted.");
  const cancel = (l) =>
    window.confirm("Cancel this request?") && run(() => api.delete(`/leaves/${l.id}`), "Request cancelled.");
  const decide = (l, status) =>
    run(() => api.put(`/admin/leaves/${l.id}/status`, { status }), `Request ${status.toLowerCase()}.`);

  const switchTab = (t) => { setTab(t); setFilters({ status: "", type: "" }); setForm(null); setNotice(""); };
  const filtered = filters.status || filters.type;

  return (
    <div className="shell">
      <header className="top">
        <div>
          <strong>Leave Manager</strong>
          <span className="muted"> {user.name} ({label(user.role)})</span>
        </div>
        <button onClick={logout}>Sign out</button>
      </header>

      {isAdmin && (
        <nav className="tabs">
          <button className={tab === "mine" ? "on" : ""} onClick={() => switchTab("mine")}>My leave</button>
          <button className={tab === "all" ? "on" : ""} onClick={() => switchTab("all")}>All requests</button>
        </nav>
      )}

      <main>
        {notice && <div className="banner ok" role="status">{notice}</div>}
        {error && (
          <div className="banner error" role="alert">
            {error} <button onClick={load}>Retry</button>
          </div>
        )}

        {form && <LeaveForm initial={form} busy={busy} onSubmit={save} onCancel={() => setForm(null)} />}

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
                  {tab === "mine" && l.status === "PENDING" && (
                    <>
                      <button onClick={() => { setNotice(""); setForm(l); }}>Edit</button>
                      <button className="danger" disabled={busy} onClick={() => cancel(l)}>Cancel</button>
                    </>
                  )}
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
