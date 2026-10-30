import React, { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { ArrowLeft, Search } from "lucide-react";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";
import NotificationBell from "../components/NotificationBell";
import SettribeLogo from "../components/SettribeLogo";

export default function AuditLog() {
  const { user } = useAuth();
  const [filters, setFilters] = useState({ actor: "", action: "", entityType: "", from: "", to: "" });
  const [applied, setApplied] = useState(filters);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!["ADMIN", "HR"].includes(user?.role)) return;
    let active = true;
    setLoading(true);
    api.get("/audit", { params: { ...applied, page, size: 25 } })
      .then(({ data }) => { if (active) setResult(data); })
      .catch((failure) => { if (active) setError(errMsg(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user?.role, applied, page]);

  if (!["ADMIN", "HR"].includes(user?.role)) return <Navigate to="/dashboard" replace />;

  const update = (key, value) => setFilters((current) => ({ ...current, [key]: value }));
  const submit = (event) => {
    event.preventDefault();
    if (filters.from && filters.to && filters.from >= filters.to) {
      setError("The end time must be later than the start time.");
      return;
    }
    setError("");
    setPage(0);
    setApplied(filters);
  };

  return (
    <div className="dashboard-page">
      <nav className="site-nav" aria-label="Main navigation"><div className="nav-container">
        <Link to="/dashboard" className="nav-brand" aria-label="Back to dashboard"><SettribeLogo height={34} /></Link>
        <div className="nav-actions"><NotificationBell /></div>
      </div></nav>
      <main className="dashboard-container">
        <Link to="/dashboard" className="btn btn-ghost btn-sm"><ArrowLeft size={14} /> Back to dashboard</Link>
        <header className="dashboard-header" style={{ marginTop: 20 }}><div className="dashboard-title-area">
          <h1>Audit history</h1><p className="muted">Append-only records of business and administrative actions.</p>
        </div></header>
        <form className="request-card audit-filters" onSubmit={submit}>
          <label><span className="form-label">Actor email</span><input className="form-input" value={filters.actor} onChange={(event) => update("actor", event.target.value)} /></label>
          <label><span className="form-label">Action</span><input className="form-input" value={filters.action} onChange={(event) => update("action", event.target.value)} /></label>
          <label><span className="form-label">Record type</span><input className="form-input" value={filters.entityType} onChange={(event) => update("entityType", event.target.value)} /></label>
          <label><span className="form-label">From</span><input className="form-input" type="datetime-local" value={filters.from} onChange={(event) => update("from", event.target.value)} /></label>
          <label><span className="form-label">To</span><input className="form-input" type="datetime-local" value={filters.to} onChange={(event) => update("to", event.target.value)} /></label>
          <button className="btn btn-accent" type="submit"><Search size={14} /> Search</button>
        </form>
        {error && <div className="banner error" role="alert">{error}</div>}
        {loading && <p role="status">Loading audit records…</p>}
        {!loading && result && <>
          {result.content.length === 0 ? <div className="request-card audit-empty">No audit records match these filters.</div> : (
            <div className="audit-table-wrap"><table className="audit-table">
              <thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Record</th><th>Change summary</th><th>Outcome</th></tr></thead>
              <tbody>{result.content.map((entry) => <tr key={entry.id}>
                <td>{new Date(entry.createdAt).toLocaleString()}</td>
                <td>{entry.actorName ?? "System"}<span className="muted">{entry.actorEmail}</span></td>
                <td>{entry.action}</td>
                <td>{entry.entityType}{entry.entityId ? ` · ${entry.entityId}` : ""}</td>
                <td>{entry.changeSummary}</td>
                <td>{entry.outcome}</td>
              </tr>)}</tbody>
            </table></div>
          )}
          <div className="audit-pagination">
            <span className="muted">Page {result.number + 1} of {Math.max(result.totalPages, 1)} · {result.totalElements} records</span>
            <div><button className="btn btn-secondary btn-sm" disabled={result.first} onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</button>
              <button className="btn btn-secondary btn-sm" disabled={result.last} onClick={() => setPage((current) => current + 1)}>Next</button></div>
          </div>
        </>}
      </main>
    </div>
  );
}
