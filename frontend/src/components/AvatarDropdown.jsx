import React, { useEffect, useRef, useState } from "react";
import {
  ChevronDown,
  User,
  BadgeCheck,
  Building2,
  Phone,
  LogOut,
  Sun,
  Moon,
  Shield,
} from "lucide-react";

/**
 * AvatarDropdown — User profile button + flyout menu
 *
 * Renders a circular avatar button showing the user's initials.
 * Clicking it opens a polished dropdown with:
 *  - Full profile info (name, email, ID, department, designation, phone)
 *  - Dark/light mode toggle
 *  - Sign out action
 *
 * @param {{ user: object, darkMode: boolean, onToggleDark: () => void, onLogout: () => void }} props
 */
export default function AvatarDropdown({ user, darkMode, onToggleDark, onLogout }) {
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef(null);

  // Close on outside click
  useEffect(() => {
    const handler = (e) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  // Close on Escape key
  useEffect(() => {
    if (!open) return;
    const handler = (e) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [open]);

  const isAdmin = user?.role === "ADMIN";

  /** Generate 1–2 capital initials from user's full name */
  const initials = user?.name
    ? user.name
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map((w) => w[0]?.toUpperCase() ?? "")
        .join("")
    : "U";

  return (
    <div className="avatar-dropdown-wrapper" ref={wrapperRef}>
      {/* ── Trigger Button ───────────────────────────────────────────── */}
      <button
        id="avatar-menu-btn"
        className="avatar-btn"
        onClick={() => setOpen((p) => !p)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls="avatar-menu"
      >
        <span className="avatar-circle" aria-hidden="true">
          {initials}
        </span>
        <span className="avatar-name">{user?.name ?? "Account"}</span>
        <ChevronDown
          size={14}
          className={`avatar-chevron ${open ? "open" : ""}`}
          aria-hidden="true"
        />
      </button>

      {/* ── Dropdown Panel ───────────────────────────────────────────── */}
      {open && (
        <div
          id="avatar-menu"
          className="avatar-dropdown"
          role="menu"
          aria-labelledby="avatar-menu-btn"
        >
          {/* Profile Header */}
          <div className="dropdown-profile-section">
            <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "10px" }}>
              <div
                className="avatar-circle"
                style={{ width: 40, height: 40, fontSize: "1rem" }}
                aria-hidden="true"
              >
                {initials}
              </div>
              <div>
                <div className="dropdown-profile-name">{user?.name}</div>
                <div className="dropdown-profile-email">{user?.email}</div>
              </div>
            </div>

            {/* Role badge */}
            <span
              className={`badge badge-role ${isAdmin ? "admin" : ""}`}
              style={{ marginBottom: "8px" }}
            >
              {isAdmin ? (
                <><Shield size={10} style={{ display: "inline" }} /> Admin</>
              ) : (
                <><BadgeCheck size={10} style={{ display: "inline" }} /> Employee</>
              )}
            </span>

            {/* Extra profile metadata rows */}
            {user?.employeeId && (
              <div className="dropdown-meta-row">
                <BadgeCheck size={12} color="var(--ink-muted)" />
                <span>{user.employeeId}</span>
              </div>
            )}
            {user?.department && (
              <div className="dropdown-meta-row">
                <Building2 size={12} color="var(--ink-muted)" />
                <span>{user.department}</span>
                {user?.designation && (
                  <span style={{ color: "var(--ink-muted)" }}>· {user.designation}</span>
                )}
              </div>
            )}
            {user?.phoneNumber && (
              <div className="dropdown-meta-row">
                <Phone size={12} color="var(--ink-muted)" />
                <span>{user.phoneNumber}</span>
              </div>
            )}
          </div>

          {/* Dark Mode Toggle */}
          <button
            className="dropdown-item"
            role="menuitem"
            onClick={() => {
              onToggleDark();
              setOpen(false);
            }}
          >
            {darkMode ? (
              <>
                <Sun size={15} color="var(--brand-lime)" />
                <span>Switch to Light Mode</span>
              </>
            ) : (
              <>
                <Moon size={15} color="var(--ink-muted)" />
                <span>Switch to Dark Mode</span>
              </>
            )}
          </button>

          <div className="dropdown-divider" role="separator" />

          {/* Sign Out */}
          <button
            className="dropdown-item danger"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              onLogout();
            }}
          >
            <LogOut size={15} />
            <span>Sign Out</span>
          </button>
        </div>
      )}
    </div>
  );
}
