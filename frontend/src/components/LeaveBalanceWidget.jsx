import React from "react";
import { CalendarDays, HeartPulse, Umbrella, ClipboardList } from "lucide-react";

/**
 * Leave balance configuration:
 * Maps leave type → { label, total quota, icon, bar color }
 *
 * The `total` is a typical enterprise allocation. The actual remaining
 * count is derived from approved leaves consumed from the server data.
 */
const BALANCE_CONFIG = [
  {
    type: "ANNUAL",
    label: "Annual Leave",
    total: 18,
    icon: CalendarDays,
    color: "#16a34a",
    bg: "rgba(22,163,74,0.1)",
  },
  {
    type: "SICK",
    label: "Sick Leave",
    total: 10,
    icon: HeartPulse,
    color: "#0891b2",
    bg: "rgba(8,145,178,0.1)",
  },
  {
    type: "CASUAL",
    label: "Casual Leave",
    total: 8,
    icon: Umbrella,
    color: "#7c3aed",
    bg: "rgba(124,58,237,0.1)",
  },
  {
    type: "UNPAID",
    label: "Unpaid Leave",
    total: null, // unlimited
    icon: ClipboardList,
    color: "#b45309",
    bg: "rgba(180,83,9,0.1)",
  },
];

/**
 * Counts how many days of each approved leave type an employee has consumed.
 *
 * @param {Array}  items       - The user's leave request list from the API
 * @returns {Object}           - Map of leaveType → consumedDays
 */
function computeConsumed(items) {
  const result = {};
  for (const item of items) {
    if (item.status !== "APPROVED") continue;
    const from = new Date(item.fromDate);
    const to = new Date(item.toDate);
    const days = Math.max(1, Math.round((to - from) / 86400000) + 1);
    result[item.leaveType] = (result[item.leaveType] ?? 0) + days;
  }
  return result;
}

/**
 * LeaveBalanceWidget
 *
 * Displays a card with per-leave-type progress bars showing:
 *   Remaining = Total - Consumed (from approved leaves)
 *
 * @param {{ items: Array }} props - items: the user's leave requests
 */
export default function LeaveBalanceWidget({ items = [] }) {
  const consumed = computeConsumed(items);

  return (
    <div className="balance-card" aria-label="Leave Allowance Balance">
      <div className="balance-title">
        <CalendarDays size={17} color="var(--brand-green)" />
        Leave Allowance Balance
      </div>

      <div className="balance-grid">
        {BALANCE_CONFIG.map(({ type, label, total, icon: Icon, color, bg }) => {
          const used = consumed[type] ?? 0;
          const isUnlimited = total === null;
          const remaining = isUnlimited ? null : Math.max(0, total - used);
          const pct = isUnlimited ? 100 : total === 0 ? 0 : Math.min(100, (remaining / total) * 100);

          return (
            <div key={type} className="balance-item" aria-label={`${label} balance`}>
              <div className="balance-item-header">
                <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                  <div
                    style={{
                      width: 26,
                      height: 26,
                      borderRadius: "6px",
                      background: bg,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                    }}
                  >
                    <Icon size={13} color={color} />
                  </div>
                  <span className="balance-label">{label}</span>
                </div>

                <span className="balance-count" style={{ color }}>
                  {isUnlimited ? (
                    <span style={{ fontSize: "0.75rem", color: "var(--ink-muted)" }}>Unlimited</span>
                  ) : (
                    `${remaining}/${total}`
                  )}
                </span>
              </div>

              {/* Progress Bar */}
              <div
                className="balance-bar-track"
                role="progressbar"
                aria-valuenow={pct}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label={`${remaining ?? "∞"} days remaining`}
              >
                <div
                  className="balance-bar-fill"
                  style={{
                    width: `${pct}%`,
                    background: pct < 25
                      ? "linear-gradient(90deg, #ef4444, #f97316)"
                      : pct < 50
                      ? "linear-gradient(90deg, #f59e0b, #fbbf24)"
                      : `linear-gradient(90deg, ${color}, ${color}aa)`,
                  }}
                />
              </div>

              <div className="balance-remaining">
                {isUnlimited
                  ? `${used} day${used !== 1 ? "s" : ""} taken`
                  : `${used} day${used !== 1 ? "s" : ""} used · ${remaining} remaining`}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
