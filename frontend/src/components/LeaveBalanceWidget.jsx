import React from "react";
import { CalendarDays, HeartPulse, Umbrella, ClipboardList } from "lucide-react";

const LEAVE_TYPES = [
  { type: "ANNUAL", label: "Annual Leave", icon: CalendarDays, color: "#16a34a", bg: "rgba(22,163,74,0.1)" },
  { type: "SICK", label: "Sick Leave", icon: HeartPulse, color: "#0891b2", bg: "rgba(8,145,178,0.1)" },
  { type: "CASUAL", label: "Casual Leave", icon: Umbrella, color: "#7c3aed", bg: "rgba(124,58,237,0.1)" },
  { type: "UNPAID", label: "Unpaid Leave", icon: ClipboardList, color: "#b45309", bg: "rgba(180,83,9,0.1)" },
];

function computeApprovedDays(items) {
  const totals = {};
  for (const item of items) {
    if (item.status !== "APPROVED") continue;
    const days = Math.max(1, Math.round((Date.parse(item.toDate) - Date.parse(item.fromDate)) / 86400000) + 1);
    totals[item.leaveType] = (totals[item.leaveType] ?? 0) + days;
  }
  return totals;
}

export default function LeaveBalanceWidget({ items = [] }) {
  const totals = computeApprovedDays(items);

  return (
    <section className="balance-card" aria-label="Approved leave usage">
      <div className="balance-title">
        <CalendarDays size={17} color="var(--brand-green)" />
        Approved leave usage
      </div>
      <p className="muted">Balance entitlements are unavailable until your organization configures a leave policy. Usage uses inclusive calendar days.</p>
      <div className="balance-grid">
        {LEAVE_TYPES.map(({ type, label, icon: Icon, color, bg }) => (
          <div key={type} className="balance-item" aria-label={`${label} approved usage`}>
            <div className="balance-item-header">
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <div style={{ width: 26, height: 26, borderRadius: 6, background: bg, display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <Icon size={13} color={color} />
                </div>
                <span className="balance-label">{label}</span>
              </div>
              <span className="balance-count" style={{ color }}>{totals[type] ?? 0}</span>
            </div>
            <div className="balance-remaining">
              {totals[type] ?? 0} approved day{totals[type] === 1 ? "" : "s"}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
