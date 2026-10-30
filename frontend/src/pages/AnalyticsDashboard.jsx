import React, { useEffect, useMemo, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { Bar, Doughnut, Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  ArcElement,
  BarElement,
  CategoryScale,
  Filler,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Tooltip,
} from "chart.js";
import { ArrowLeft, BarChart3 } from "lucide-react";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";
import NotificationBell from "../components/NotificationBell";
import SettribeLogo from "../components/SettribeLogo";

ChartJS.register(ArcElement, BarElement, CategoryScale, Filler, Legend, LinearScale, LineElement, PointElement, Tooltip);

function isoDate(date) {
  return date.toISOString().slice(0, 10);
}

const palette = ["#16a34a", "#0891b2", "#7c3aed", "#f59e0b", "#e11d48", "#2563eb"];

export default function AnalyticsDashboard() {
  const { user } = useAuth();
  const endDefault = isoDate(new Date());
  const startDefault = isoDate(new Date(Date.now() - 365 * 86400000));
  const [fromDate, setFromDate] = useState(startDefault);
  const [toDate, setToDate] = useState(endDefault);
  const [department, setDepartment] = useState("");
  const [leaveType, setLeaveType] = useState("");
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!["ADMIN", "HR"].includes(user?.role)) return;
    if (!fromDate || !toDate || fromDate > toDate) {
      setError("Choose a valid date range.");
      setLoading(false);
      setData(null);
      return;
    }
    let active = true;
    setLoading(true);
    setError("");
    api.get("/dashboard/hr", { params: { fromDate, toDate, department: department || undefined, leaveType: leaveType || undefined } })
      .then(({ data: report }) => { if (active) setData(report); })
      .catch((failure) => { if (active) setError(errMsg(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [fromDate, toDate, department, leaveType, user?.role]);

  const monthEntries = Object.entries(data?.monthlyApprovedRequests ?? {});
  const departmentEntries = Object.entries(data?.requestsByDepartment ?? {});
  const typeEntries = Object.entries(data?.requestsByLeaveType ?? {});
  const monthData = useMemo(() => ({
    labels: monthEntries.map(([month]) => month),
    datasets: [{ label: "Approved leave requests", data: monthEntries.map(([, count]) => count), borderColor: "#16a34a", backgroundColor: "rgba(22,163,74,.15)", fill: true, tension: 0.25 }],
  }), [data]);
  const departmentData = useMemo(() => ({
    labels: departmentEntries.map(([name]) => name),
    datasets: [{ label: "Approved requests", data: departmentEntries.map(([, count]) => count), backgroundColor: palette }],
  }), [data]);
  const typeData = useMemo(() => ({
    labels: typeEntries.map(([name]) => name),
    datasets: [{ label: "Approved requests", data: typeEntries.map(([, count]) => count), backgroundColor: palette }],
  }), [data]);

  if (!["ADMIN", "HR"].includes(user?.role)) return <Navigate to="/dashboard" replace />;

  return (
    <div className="dashboard-page">
      <nav className="site-nav" aria-label="Main navigation">
        <div className="nav-container">
          <Link to="/dashboard" className="nav-brand" aria-label="Back to dashboard"><SettribeLogo height={34} /></Link>
          <div className="nav-actions"><NotificationBell /></div>
        </div>
      </nav>
      <main className="dashboard-container">
        <Link to="/dashboard" className="btn btn-ghost btn-sm"><ArrowLeft size={14} /> Back to dashboard</Link>
        <header className="dashboard-header" style={{ marginTop: 20 }}>
          <div className="dashboard-title-area">
            <h1><BarChart3 size={23} aria-hidden="true" /> Leave analytics</h1>
            <p className="muted">Aggregated approved leave activity from recorded requests.</p>
          </div>
        </header>
        <form className="request-card" style={{ display: "flex", alignItems: "end", gap: 12, flexWrap: "wrap", padding: 18 }} onSubmit={(event) => event.preventDefault()}>
          <label className="form-group"><span className="form-label">Start date</span><input className="form-input" type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} /></label>
          <label className="form-group"><span className="form-label">End date</span><input className="form-input" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} /></label>
          <label className="form-group"><span className="form-label">Department</span><input className="form-input" value={department} onChange={(event) => setDepartment(event.target.value)} placeholder="All departments" /></label>
          <label className="form-group"><span className="form-label">Leave type</span>
            <select className="form-input" value={leaveType} onChange={(event) => setLeaveType(event.target.value)}>
              <option value="">All types</option><option value="ANNUAL">Annual</option><option value="SICK">Sick</option><option value="CASUAL">Casual</option><option value="UNPAID">Unpaid</option>
            </select>
          </label>
        </form>
        {error && <div className="banner error" role="alert">{error}</div>}
        {loading && <p role="status">Loading analytics…</p>}
        {!loading && data && (
          <>
            <section className="metrics-grid" aria-label="Request status totals">
              {Object.entries(data.requestsByStatus).map(([status, count]) => (
                <div key={status} className="metric-card"><div className="metric-title">{status}</div><div className="metric-value">{count}</div><div className="metric-subtitle">Requests in selected period</div></div>
              ))}
            </section>
            <section className="analytics-chart-grid" aria-label="Leave charts">
              <article className="request-card analytics-chart-card">
                <h2>Monthly approved leave trend</h2>
                {monthEntries.length ? <Line data={monthData} options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { display: true } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }} /> : <p className="muted">No approved leave in this period.</p>}
                <table className="sr-only"><caption>Monthly approved leave request counts</caption><tbody>{monthEntries.map(([month, count]) => <tr key={month}><th>{month}</th><td>{count}</td></tr>)}</tbody></table>
              </article>
              <article className="request-card analytics-chart-card">
                <h2>Approved requests by department</h2>
                {departmentEntries.length ? <Bar data={departmentData} options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }} /> : <p className="muted">No department totals for this period.</p>}
                <table className="sr-only"><caption>Approved leave requests by department</caption><tbody>{departmentEntries.map(([name, count]) => <tr key={name}><th>{name}</th><td>{count}</td></tr>)}</tbody></table>
              </article>
              <article className="request-card analytics-chart-card">
                <h2>Approved requests by leave type</h2>
                {typeEntries.length ? <Doughnut data={typeData} options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { position: "bottom" } } }} /> : <p className="muted">No leave-type totals for this period.</p>}
                <table className="sr-only"><caption>Approved leave requests by leave type</caption><tbody>{typeEntries.map(([name, count]) => <tr key={name}><th>{name}</th><td>{count}</td></tr>)}</tbody></table>
              </article>
            </section>
          </>
        )}
      </main>
    </div>
  );
}
