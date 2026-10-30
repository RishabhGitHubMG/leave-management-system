import React, { useEffect, useState } from "react";
import { Link, Navigate, useLocation } from "react-router-dom";
import { ArrowLeft, Download, FileSpreadsheet, Search } from "lucide-react";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";
import NotificationBell from "../components/NotificationBell";
import SettribeLogo from "../components/SettribeLogo";

const initialFilters = { fromDate: "", toDate: "", department: "", employeeId: "", leaveType: "", status: "" };

function nonEmptyParams(values) {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== "" && value != null));
}

function saveBlob(blob, name) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export default function Reports() {
  const { user } = useAuth();
  const location = useLocation();
  const payroll = location.pathname === "/reports/payroll";
  const payrollFilters = { ...initialFilters, leaveType: "UNPAID", status: "APPROVED" };
  const [filters, setFilters] = useState(payroll ? payrollFilters : initialFilters);
  const [applied, setApplied] = useState(payroll ? payrollFilters : initialFilters);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    if (payroll && !["ADMIN", "HR"].includes(user?.role)) return;
    let active = true;
    setLoading(true);
    setError("");
    api.get("/reports/leaves", { params: { ...nonEmptyParams(applied), ...(payroll ? { leaveType: "UNPAID", status: "APPROVED" } : {}), page, size: 25 } })
      .then(({ data }) => { if (active) setResult(data); })
      .catch((failure) => { if (active) setError(errMsg(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [applied, page, payroll, user?.role]);

  if (payroll && !["ADMIN", "HR"].includes(user?.role)) return <Navigate to="/reports" replace />;

  const update = (key, value) => setFilters((current) => ({ ...current, [key]: value }));
  const search = (event) => {
    event.preventDefault();
    if (filters.fromDate && filters.toDate && filters.fromDate > filters.toDate) {
      setError("The end date must be on or after the start date.");
      return;
    }
    setPage(0);
    setApplied(filters);
    setError("");
    setNotice("");
  };

  const downloadCsv = async () => {
    setExporting(true);
    setError("");
    setNotice("");
    try {
      const { data } = await api.get("/reports/leaves.csv", { params: { ...nonEmptyParams(applied), ...(payroll ? { leaveType: "UNPAID", status: "APPROVED" } : {}) }, responseType: "blob" });
      saveBlob(data, "leave-report.csv");
      setNotice("CSV report generated.");
    } catch (failure) {
      setError(errMsg(failure));
    } finally {
      setExporting(false);
    }
  };

  const downloadPayroll = async () => {
    setExporting(true);
    setError("");
    setNotice("");
    try {
      const { data } = await api.get("/reports/payroll/unpaid-leave.csv", {
        params: { fromDate: applied.fromDate, toDate: applied.toDate }, responseType: "blob",
      });
      saveBlob(data, "unpaid-leave-payroll.csv");
      setNotice("Payroll file generated. No salary deduction has been calculated.");
    } catch (failure) {
      setError(errMsg(failure));
    } finally {
      setExporting(false);
    }
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
          <h1>{payroll ? "Unpaid leave payroll export" : "Leave reports"}</h1>
          <p className="muted">{payroll ? "Approved unpaid leave only. Workdays and salary deductions are not inferred." : "Records are scoped to your own requests or the employees you are authorized to view."}</p>
        </div></header>
        <form className="request-card audit-filters" onSubmit={search}>
          <label><span className="form-label">Start date</span><input className="form-input" type="date" value={filters.fromDate} onChange={(event) => update("fromDate", event.target.value)} /></label>
          <label><span className="form-label">End date</span><input className="form-input" type="date" value={filters.toDate} onChange={(event) => update("toDate", event.target.value)} /></label>
          {!payroll && <>
            {user?.role !== "EMPLOYEE" && <label><span className="form-label">Department</span><input className="form-input" value={filters.department} onChange={(event) => update("department", event.target.value)} /></label>}
            {user?.role !== "EMPLOYEE" && <label><span className="form-label">Employee record ID</span><input className="form-input" type="number" min="1" value={filters.employeeId} onChange={(event) => update("employeeId", event.target.value)} /></label>}
            <label><span className="form-label">Leave type</span><select className="form-input" value={filters.leaveType} onChange={(event) => update("leaveType", event.target.value)}>
              <option value="">All types</option><option value="ANNUAL">Annual</option><option value="SICK">Sick</option><option value="CASUAL">Casual</option><option value="UNPAID">Unpaid</option>
            </select></label>
            <label><span className="form-label">Status</span><select className="form-input" value={filters.status} onChange={(event) => update("status", event.target.value)}>
              <option value="">All statuses</option><option value="PENDING">Pending</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option><option value="CANCELLED">Cancelled</option>
            </select></label>
          </>}
          <button className="btn btn-accent" type="submit"><Search size={14} /> Apply filters</button>
        </form>
        {error && <div className="banner error" role="alert">{error}</div>}
        {notice && <div className="banner ok" role="status">{notice}</div>}
        <div className="report-actions">
          <button className="btn btn-secondary" type="button" onClick={downloadCsv} disabled={exporting || loading}><FileSpreadsheet size={15} /> Export CSV</button>
          {payroll && <button className="btn btn-accent" type="button" onClick={downloadPayroll} disabled={exporting || loading || !applied.fromDate || !applied.toDate}><Download size={15} /> Generate payroll CSV</button>}
          {!payroll && ["ADMIN", "HR"].includes(user?.role) && <Link className="btn btn-secondary" to="/reports/payroll">Payroll export</Link>}
        </div>
        {loading && <p role="status">Loading report…</p>}
        {!loading && result && <>
          {result.content.length === 0 ? <div className="request-card audit-empty">No leave requests match these filters.</div> : (
            <div className="audit-table-wrap"><table className="audit-table">
              <thead><tr><th>Employee</th><th>Department</th><th>Type</th><th>Dates</th><th>Status</th><th>Submitted</th></tr></thead>
              <tbody>{result.content.map((request) => <tr key={request.id}>
                <td>{request.userName}<span className="muted">{request.userEmployeeId || request.userEmail}</span></td>
                <td>{request.userDepartment || "Unassigned"}</td><td>{request.leaveType}</td>
                <td>{request.fromDate} – {request.toDate}</td><td>{request.status}</td><td>{request.createdAt ? new Date(request.createdAt).toLocaleString() : "—"}</td>
              </tr>)}</tbody>
            </table></div>
          )}
          <div className="audit-pagination"><span className="muted">Page {result.number + 1} of {Math.max(result.totalPages, 1)} · {result.totalElements} records</span>
            <div><button className="btn btn-secondary btn-sm" disabled={result.first} onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</button>
              <button className="btn btn-secondary btn-sm" disabled={result.last} onClick={() => setPage((current) => current + 1)}>Next</button></div>
          </div>
        </>}
      </main>
    </div>
  );
}
