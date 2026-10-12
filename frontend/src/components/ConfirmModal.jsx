import React, { useEffect } from "react";
import { AlertTriangle, Trash2, CheckCircle2 } from "lucide-react";

/**
 * ConfirmModal Component
 *
 * Accessible in-app confirmation modal replacing browser's window.confirm.
 * Uses Lucide icons for visual consistency with the rest of the dashboard.
 *
 * @param {Object}   props
 * @param {boolean}  props.open         - Whether the modal is visible
 * @param {string}   props.title        - Modal title heading
 * @param {string}   props.message      - Descriptive confirmation message
 * @param {string}   [props.confirmText="Confirm"] - Text for the positive action button
 * @param {string}   [props.cancelText="Cancel"]   - Text for the dismissal button
 * @param {boolean}  [props.isDestructive=true]     - Danger style on confirm button
 * @param {Function} props.onConfirm    - Callback when user confirms
 * @param {Function} props.onCancel     - Callback when user cancels
 */
export default function ConfirmModal({
  open,
  title,
  message,
  confirmText = "Confirm",
  cancelText = "Cancel",
  isDestructive = true,
  onConfirm,
  onCancel,
}) {
  useEffect(() => {
    if (!open) return;
    const handler = (e) => { if (e.key === "Escape") onCancel(); };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-modal-title"
      onClick={(e) => { if (e.target === e.currentTarget) onCancel(); }}
    >
      <div className="modal-dialog" style={{ maxWidth: "440px" }}>
        <div className="modal-header">
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            {isDestructive
              ? <AlertTriangle size={18} color="#dc2626" />
              : <CheckCircle2 size={18} color="#16a34a" />
            }
            <h2 id="confirm-modal-title" style={{ fontSize: "1.0625rem", fontWeight: 700 }}>
              {title}
            </h2>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onCancel}
            aria-label="Close dialog"
          >
            ✕
          </button>
        </div>

        <div className="modal-body">
          <p style={{ color: "var(--ink-secondary)", fontSize: "0.9375rem", lineHeight: 1.6 }}>
            {message}
          </p>
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary btn-sm" onClick={onCancel}>
            {cancelText}
          </button>
          <button
            type="button"
            className={`btn btn-sm ${isDestructive ? "btn-danger" : "btn-accent"}`}
            onClick={onConfirm}
          >
            {isDestructive && <Trash2 size={13} />}
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
