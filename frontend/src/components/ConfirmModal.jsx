import React, { useEffect } from "react";

/**
 * ConfirmModal Component
 * 
 * Accessible in-app confirmation modal replacing browser's window.confirm.
 * 
 * @param {Object} props
 * @param {boolean} props.open - Whether the modal is visible
 * @param {string} props.title - Modal title heading
 * @param {string} props.message - Descriptive confirmation message
 * @param {string} [props.confirmText="Confirm"] - Text for positive action
 * @param {string} [props.cancelText="Cancel"] - Text for dismissal
 * @param {boolean} [props.isDestructive=true] - Whether the action is destructive (danger color)
 * @param {Function} props.onConfirm - Callback when user confirms
 * @param {Function} props.onCancel - Callback when user cancels
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
    const handleKeyDown = (e) => {
      if (e.key === "Escape") onCancel();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-modal-title"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div className="modal-dialog" style={{ maxWidth: "440px" }}>
        <div className="modal-header">
          <h2 id="confirm-modal-title" style={{ fontSize: "1.125rem" }}>
            {title}
          </h2>
          <button type="button" className="modal-close-btn" onClick={onCancel} aria-label="Close dialog">
            ✕
          </button>
        </div>

        <div className="modal-body">
          <p style={{ color: "var(--ink-secondary)", fontSize: "0.9375rem", lineHeight: 1.5 }}>
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
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
