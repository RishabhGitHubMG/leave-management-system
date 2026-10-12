import { useState } from "react";

/**
 * Supported leave types selectable by an employee.
 */
export const TYPES = ["SICK", "CASUAL", "ANNUAL", "UNPAID"];

/**
 * Formats enum strings to title case for friendly UI labels (e.g. "SICK" -> "Sick").
 *
 * @param {string} s - Uppercase enum token
 * @returns {string} Formatted label
 */
export const label = (s) => s.charAt(0) + s.slice(1).toLowerCase();

/**
 * LeaveForm Component
 *
 * Controlled form used for both creating a new leave request and editing an existing one.
 *
 * @param {Object} props
 * @param {Object} props.initial - Initial values (empty object for new, or leave request object for edit)
 * @param {boolean} props.busy - Indicates if an API submission is currently in flight
 * @param {Function} props.onSubmit - Callback invoked with form payload when inputs are valid
 * @param {Function} props.onCancel - Callback invoked when the user dismisses the form
 */
export default function LeaveForm({ initial, busy, onSubmit, onCancel }) {
  // Form input state initialized with existing values if editing, or safe defaults
  const [f, setF] = useState({
    leaveType: initial.leaveType || "SICK",
    fromDate: initial.fromDate || "",
    toDate: initial.toDate || "",
    reason: initial.reason || "",
  });

  // Client-side validation error message
  const [err, setErr] = useState("");

  // Curried change handler for updating input fields
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  /**
   * Validates form inputs and dispatches the payload to the onSubmit handler.
   */
  const submit = (e) => {
    e.preventDefault();

    // Verify all required fields are present
    if (!f.fromDate || !f.toDate || !f.reason.trim()) {
      return setErr("Fill in every field.");
    }

    // Chronological validation: end date cannot precede start date
    if (f.toDate < f.fromDate) {
      return setErr("The end date can't be before the start date.");
    }

    setErr("");
    onSubmit({ ...f, reason: f.reason.trim() });
  };

  return (
    <form className="panel" onSubmit={submit} noValidate>
      {/* Dynamic heading depending on edit vs create mode */}
      <h2>{initial.id ? "Edit request" : "Apply for leave"}</h2>

      {/* Validation alert banner */}
      {err && <div className="banner error" role="alert">{err}</div>}

      {/* Leave type dropdown selector */}
      <label>Leave type
        <select value={f.leaveType} onChange={set("leaveType")}>
          {TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
        </select>
      </label>

      {/* Start and end date pickers */}
      <div className="row">
        <label>From<input type="date" value={f.fromDate} onChange={set("fromDate")} /></label>
        <label>To<input type="date" value={f.toDate} min={f.fromDate} onChange={set("toDate")} /></label>
      </div>

      {/* Reason text area with maximum character length limit */}
      <label>Reason
        <textarea rows="3" maxLength="500" value={f.reason} onChange={set("reason")} />
      </label>

      {/* Action buttons */}
      <div className="row actions">
        <button type="button" onClick={onCancel}>Close</button>
        <button className="primary" disabled={busy}>
          {busy ? "Saving..." : initial.id ? "Save changes" : "Submit request"}
        </button>
      </div>
    </form>
  );
}

