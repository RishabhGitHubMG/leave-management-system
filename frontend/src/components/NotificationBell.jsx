import React, { useCallback, useEffect, useRef, useState } from "react";
import { Bell, CheckCheck } from "lucide-react";
import api, { errMsg } from "../api";

const POLL_INTERVAL_MS = 60000;

function fmtTime(iso) {
  if (!iso) return "";
  return new Date(iso).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

/**
 * NotificationBell — in-app notification trigger with unread badge and dropdown list.
 * The unread count is polled periodically; the list loads when the dropdown opens.
 */
export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const wrapperRef = useRef(null);

  const refreshCount = useCallback(async () => {
    try {
      const { data } = await api.get("/notifications/unread-count");
      setUnreadCount(data?.count ?? 0);
    } catch {
      // Badge keeps its last known value; the next poll retries
    }
  }, []);

  const loadItems = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const { data } = await api.get("/notifications");
      setItems(data);
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshCount();
    const timer = setInterval(refreshCount, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refreshCount]);

  useEffect(() => {
    if (open) loadItems();
  }, [open, loadItems]);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) setOpen(false);
    };
    const onKeyDown = (e) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  const syncAfterFailure = (e) => {
    setError(errMsg(e));
    loadItems();
    refreshCount();
  };

  const markRead = async (notification) => {
    if (notification.read) return;
    setItems((prev) => prev.map((n) => (n.id === notification.id ? { ...n, read: true } : n)));
    setUnreadCount((c) => Math.max(0, c - 1));
    try {
      await api.put(`/notifications/${notification.id}/read`);
    } catch (e) {
      syncAfterFailure(e);
    }
  };

  const markAllRead = async () => {
    setItems((prev) => prev.map((n) => ({ ...n, read: true })));
    setUnreadCount(0);
    try {
      await api.put("/notifications/read-all");
    } catch (e) {
      syncAfterFailure(e);
    }
  };

  const hasUnread = unreadCount > 0;
  const badgeText = unreadCount > 99 ? "99+" : String(unreadCount);

  return (
    <div className="notification-wrapper" ref={wrapperRef}>
      <button
        type="button"
        className="notification-btn"
        onClick={() => setOpen((p) => !p)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls="notification-panel"
        aria-label={hasUnread ? `Notifications, ${unreadCount} unread` : "Notifications"}
      >
        <Bell size={18} />
        {hasUnread && (
          <span className="notification-badge" aria-hidden="true">{badgeText}</span>
        )}
      </button>

      {open && (
        <div id="notification-panel" className="notification-dropdown" role="region" aria-label="Notifications">
          <div className="notification-header">
            <span className="notification-title">Notifications</span>
            {hasUnread && (
              <button type="button" className="notification-mark-all" onClick={markAllRead}>
                <CheckCheck size={13} /> Mark all as read
              </button>
            )}
          </div>

          {error && <div className="notification-error" role="alert">{error}</div>}

          {!loading && items.length === 0 && !error && (
            <div className="notification-empty">You have no notifications yet.</div>
          )}

          <ul className="notification-list">
            {items.map((n) => (
              <li key={n.id}>
                <button
                  type="button"
                  className={`notification-item ${n.read ? "" : "is-unread"}`}
                  onClick={() => markRead(n)}
                >
                  <span className="notification-message">{n.message}</span>
                  <span className="notification-time">{fmtTime(n.createdAt)}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
