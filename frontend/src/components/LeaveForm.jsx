import { useState } from "react";

export const TYPES = ["SICK", "CASUAL", "ANNUAL", "UNPAID"];
export const label = (s) => s.charAt(0) + s.slice(1).toLowerCase();

export default function LeaveForm({ initial, busy, onSubmit, onCancel }) {
  const [f, setF] = useState({
    leaveType: initial.leaveType || "SICK",
    fromDate: initial.fromDate || "",
    toDate: initial.toDate || "",
    reason: initial.reason || "",
  });
  const [err, setErr] = useState("");
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const submit = (e) => {
    e.preventDefault();
    if (!f.fromDate || !f.toDate || !f.reason.trim()) return setErr("Fill in every field.");
    if (f.toDate < f.fromDate) return setErr("The end date can't be before the start date.");
    setErr("");
    onSubmit({ ...f, reason: f.reason.trim() });
  };

  return (
    <form className="panel" onSubmit={submit} noValidate>
      <h2>{initial.id ? "Edit request" : "Apply for leave"}</h2>
      {err && <div className="banner error" role="alert">{err}</div>}
      <label>Leave type
        <select value={f.leaveType} onChange={set("leaveType")}>
          {TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
        </select>
      </label>
      <div className="row">
        <label>From<input type="date" value={f.fromDate} onChange={set("fromDate")} /></label>
        <label>To<input type="date" value={f.toDate} min={f.fromDate} onChange={set("toDate")} /></label>
      </div>
      <label>Reason
        <textarea rows="3" maxLength="500" value={f.reason} onChange={set("reason")} />
      </label>
      <div className="row actions">
        <button type="button" onClick={onCancel}>Close</button>
        <button className="primary" disabled={busy}>{busy ? "Saving..." : initial.id ? "Save changes" : "Submit request"}</button>
      </div>
    </form>
  );
}
