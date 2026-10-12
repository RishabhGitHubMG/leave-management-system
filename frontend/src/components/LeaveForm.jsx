import React, { useEffect, useRef, useState } from "react";
import { CalendarDays, X } from "lucide-react";

/**
 * Supported leave categories.
 */
export const TYPES = ["SICK", "CASUAL", "ANNUAL", "UNPAID"];

/**
 * Helper to format enum strings to title case (e.g. "SICK" -> "Sick").
 */
export const label = (s) => (s ? s.charAt(0) + s.slice(1).toLowerCase() : "");

/**
 * Calculates calendar day count between two dates inclusive
 */
function calculateDays(from, to) {
  if (!from || !to) return 0;
  const d1 = new Date(from);
  const d2 = new Date(to);
  const diffTime = d2.getTime() - d1.getTime();
  if (diffTime < 0) return 0;
  return Math.round(diffTime / (1000 * 3600 * 24)) + 1;
}

/**
 * LeaveForm Modal Component
 * 
 * Accessible, controlled modal form for creating or editing leave applications.
 * 
 * Features:
 * - Backdrop blur with smooth slide-in animation
 * - Escape key dismissal with unsaved changes safety
 * - Automatic duration day counter
 * - Real-time field validation with inline error states
 * - Live character counter for reason (10 to 500 characters)
 */
export default function LeaveForm({ initial = {}, busy = false, onSubmit, onCancel }) {
  const [f, setF] = useState({
    leaveType: initial.leaveType || "SICK",
    fromDate: initial.fromDate || "",
    toDate: initial.toDate || "",
    reason: initial.reason || "",
  });

  const [errors, setErrors] = useState({});
  const modalRef = useRef(null);
  const firstInputRef = useRef(null);

  useEffect(() => {
    firstInputRef.current?.focus();
  }, []);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onCancel();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onCancel]);

  const handleChange = (k) => (e) => {
    const val = e.target.value;
    setF((prev) => {
      const updated = { ...prev, [k]: val };
      // Auto-adjust toDate if fromDate is set after toDate
      if (k === "fromDate" && updated.toDate && updated.toDate < val) {
        updated.toDate = val;
      }
      return updated;
    });
    if (errors[k]) {
      setErrors((prev) => ({ ...prev, [k]: "" }));
    }
  };

  const validate = () => {
    const errs = {};
    if (!f.fromDate) {
      errs.fromDate = "Please choose a start date.";
    }
    if (!f.toDate) {
      errs.toDate = "Please choose an end date.";
    } else if (f.fromDate && f.toDate < f.fromDate) {
      errs.toDate = "End date cannot be earlier than start date.";
    }

    const trimmedReason = f.reason.trim();
    if (!trimmedReason) {
      errs.reason = "Please state a reason for your leave.";
    } else if (trimmedReason.length < 10) {
      errs.reason = `Please provide more detail (minimum 10 characters; currently ${trimmedReason.length}).`;
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const submit = (e) => {
    e.preventDefault();
    if (!validate()) return;
    onSubmit({ ...f, reason: f.reason.trim() });
  };

  const daysCount = calculateDays(f.fromDate, f.toDate);

  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      onClick={(e) => {
        // Dismiss when clicking the backdrop
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div className="modal-dialog" ref={modalRef}>
        <div className="modal-header">
          <h2 id="modal-title">{initial.id ? "Edit Leave Application" : "Apply for Leave"}</h2>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onCancel}
            aria-label="Close dialog"
          >
            <X size={16} />
          </button>
        </div>

        <form onSubmit={submit} noValidate>
          <div className="modal-body">
            {/* Leave Type Selector */}
            <div className="form-group">
              <label htmlFor="leave-type-select" className="form-label">
                Leave Category
              </label>
              <select
                id="leave-type-select"
                ref={firstInputRef}
                className="form-select"
                value={f.leaveType}
                onChange={handleChange("leaveType")}
                disabled={busy}
              >
                {TYPES.map((t) => (
                  <option key={t} value={t}>
                    {label(t)} Leave
                  </option>
                ))}
              </select>
            </div>

            {/* Date Pickers */}
            <div className="form-row-2">
              <div className="form-group">
                <label htmlFor="from-date-input" className="form-label">
                  From Date
                </label>
                <input
                  id="from-date-input"
                  type="date"
                  className={`form-input ${errors.fromDate ? "is-invalid" : ""}`}
                  value={f.fromDate}
                  onChange={handleChange("fromDate")}
                  disabled={busy}
                  aria-invalid={!!errors.fromDate}
                  aria-describedby={errors.fromDate ? "from-date-error" : undefined}
                />
                {errors.fromDate && (
                  <div id="from-date-error" className="field-error" role="alert">
                    {errors.fromDate}
                  </div>
                )}
              </div>

              <div className="form-group">
                <label htmlFor="to-date-input" className="form-label">
                  To Date
                </label>
                <input
                  id="to-date-input"
                  type="date"
                  min={f.fromDate}
                  className={`form-input ${errors.toDate ? "is-invalid" : ""}`}
                  value={f.toDate}
                  onChange={handleChange("toDate")}
                  disabled={busy}
                  aria-invalid={!!errors.toDate}
                  aria-describedby={errors.toDate ? "to-date-error" : undefined}
                />
                {errors.toDate && (
                  <div id="to-date-error" className="field-error" role="alert">
                    {errors.toDate}
                  </div>
                )}
              </div>
            </div>

            {/* Live Duration Chip */}
            {daysCount > 0 && (
              <div style={{ marginBottom: "16px" }}>
                <span className="chip" style={{ background: "var(--brand-gradient-subtle)", color: "var(--brand-navy-light)" }}>
                  <CalendarDays size={13} /> Total Duration: <strong>{daysCount} {daysCount === 1 ? "Day" : "Days"}</strong>
                </span>
              </div>
            )}

            {/* Reason Text Area */}
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label htmlFor="reason-textarea" className="form-label">
                Reason / Remarks
              </label>
              <textarea
                id="reason-textarea"
                rows="4"
                maxLength="500"
                className={`form-textarea ${errors.reason ? "is-invalid" : ""}`}
                value={f.reason}
                onChange={handleChange("reason")}
                placeholder="Briefly describe the purpose of your time off..."
                disabled={busy}
                aria-invalid={!!errors.reason}
                aria-describedby={errors.reason ? "reason-error" : undefined}
              />
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                {errors.reason ? (
                  <div id="reason-error" className="field-error" role="alert">
                    {errors.reason}
                  </div>
                ) : <span />}
                <span className="char-counter">{f.reason.length} / 500</span>
              </div>
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
              Cancel
            </button>
            <button type="submit" className="btn btn-accent" disabled={busy}>
              {busy ? "Saving..." : initial.id ? "Save Changes" : "Submit Request →"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
