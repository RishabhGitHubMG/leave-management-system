import React, { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { ArrowLeft, CalendarDays, Clock, TriangleAlert } from "lucide-react";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";
import NotificationBell from "../components/NotificationBell";
import SettribeLogo from "../components/SettribeLogo";

function LeaveList({ title, items, waiting = false }) {
  return (
    <section className="request-card manager-summary-card">
      <h2>{title} <span className="chip">{items.length}</span></h2>
      {items.length === 0 ? <p className="muted">No requests in this period.</p> : (
        <ul>
          {items.map((entry) => {
            const request = waiting ? entry.request : entry;
            return (
              <li key={request.id}>
                <Link to={`/dashboard?requestId=${request.id}`}>
                  <strong>{request.userName}</strong> · {request.leaveType} · {request.fromDate} to {request.toDate}
                </Link>
                {waiting && <span className="muted"> · {entry.waitingDays} day(s) waiting</span>}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

export default function ManagerDashboard() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (user?.role !== "MANAGER") return;
    let active = true;
    api.get("/dashboard/manager")
      .then(({ data: summary }) => { if (active) setData(summary); })
      .catch((failure) => { if (active) setError(errMsg(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user?.role]);

  if (user?.role !== "MANAGER") return <Navigate to="/dashboard" replace />;

  return (
    <div className="dashboard-page">
      <nav className="site-nav" aria-label="Main navigation"><div className="nav-container">
        <Link to="/dashboard" className="nav-brand" aria-label="Back to dashboard"><SettribeLogo height={34} /></Link>
        <div className="nav-actions"><NotificationBell /></div>
      </div></nav>
      <main className="dashboard-container">
        <Link to="/dashboard" className="btn btn-ghost btn-sm"><ArrowLeft size={14} /> Leave requests</Link>
        <header className="dashboard-header" style={{ marginTop: 20 }}><div className="dashboard-title-area">
          <h1>Team availability</h1><p className="muted">Your direct reports only. Approved leave is confirmed; pending requests are shown separately.</p>
        </div></header>
        {error && <div className="banner error" role="alert">{error}</div>}
        {loading && <p role="status">Loading team summary…</p>}
        {data && <>
          <section className="metrics-grid" aria-label="Team summary">
            <div className="metric-card"><Clock size={18} /><div className="metric-title">Pending approvals</div><div className="metric-value">{data.pendingApprovals.length}</div></div>
            <div className="metric-card"><CalendarDays size={18} /><div className="metric-title">Absent today</div><div className="metric-value">{data.absentToday.length}</div></div>
            <div className="metric-card"><CalendarDays size={18} /><div className="metric-title">Active direct reports</div><div className="metric-value">{data.teamSize}</div></div>
            <div className="metric-card"><CalendarDays size={18} /><div className="metric-title">Approved team days this year</div><div className="metric-value">{data.approvedTeamDaysThisYear}</div></div>
          </section>
          {data.overlapWarnings.length > 0 && <section className="request-card manager-summary-card" aria-label="Team leave overlap warnings">
            <h2><TriangleAlert size={17} /> Potential coverage overlaps</h2>
            <ul>{data.overlapWarnings.map((overlap) => <li key={`${overlap.firstRequestId}-${overlap.secondRequestId}`}>
              {overlap.firstEmployee} and {overlap.secondEmployee}: {overlap.overlapFrom} to {overlap.overlapTo}
            </li>)}</ul>
          </section>}
          <div className="manager-summary-grid">
            <LeaveList title="Pending approvals" items={data.pendingApprovals} waiting />
            <LeaveList title="Absent today" items={data.absentToday} />
            <LeaveList title="Expected this week" items={data.expectedThisWeek} />
            <LeaveList title="Upcoming approved leave" items={data.upcomingTeamLeave} />
          </div>
        </>}
      </main>
    </div>
  );
}
