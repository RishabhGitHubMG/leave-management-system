import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, Bell, CheckCircle2, Settings } from "lucide-react";
import api, { errMsg } from "../api";
import NotificationBell from "../components/NotificationBell";
import SettribeLogo from "../components/SettribeLogo";

const preferenceFields = [
  ["inAppEnabled", "In-app notifications", "Show workflow updates in the notification bell."],
  ["emailEnabled", "Email notifications", "Receive leave request updates by email when email delivery is configured."],
  ["dailyDigestEnabled", "Daily manager digest", "Receive a daily summary of team leave, when you are an authorized manager."],
  ["approvalRemindersEnabled", "Approval reminders", "Receive reminders for approvals assigned to you."],
];

export default function NotificationPreferences() {
  const [preferences, setPreferences] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;
    api.get("/notifications/preferences")
      .then(({ data }) => { if (active) setPreferences(data); })
      .catch((failure) => { if (active) setError(errMsg(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const save = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const { data } = await api.put("/notifications/preferences", preferences);
      setPreferences(data);
      setNotice("Notification preferences saved.");
    } catch (failure) {
      setError(errMsg(failure));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="dashboard-page">
      <nav className="site-nav" aria-label="Main navigation">
        <div className="nav-container">
          <Link to="/dashboard" className="nav-brand" aria-label="Back to dashboard"><SettribeLogo height={34} /></Link>
          <div className="nav-actions"><NotificationBell /></div>
        </div>
      </nav>
      <main className="dashboard-container">
        <Link to="/dashboard" className="btn btn-ghost btn-sm"><ArrowLeft size={14} /> Back to dashboard</Link>
        <header className="dashboard-header" style={{ marginTop: 20 }}>
          <div className="dashboard-title-area">
            <h1><Settings size={22} aria-hidden="true" /> Notification preferences</h1>
            <p className="muted">Choose how you receive leave-management updates.</p>
          </div>
        </header>
        {notice && <div className="banner ok" role="status"><CheckCircle2 size={16} />{notice}</div>}
        {error && <div className="banner error" role="alert">{error}</div>}
        {loading ? <p role="status">Loading preferences…</p> : preferences && (
          <form onSubmit={save} className="request-card" style={{ maxWidth: 720, padding: 24 }}>
            {preferenceFields.map(([key, title, description]) => (
              <label key={key} style={{ display: "flex", gap: 12, alignItems: "flex-start", padding: "14px 0", borderBottom: "1px solid var(--line-subtle)", cursor: "pointer" }}>
                <input
                  type="checkbox"
                  checked={Boolean(preferences[key])}
                  onChange={(event) => setPreferences((current) => ({ ...current, [key]: event.target.checked }))}
                />
                <span>
                  <strong>{title}</strong>
                  <span className="muted" style={{ display: "block", marginTop: 3 }}>{description}</span>
                </span>
              </label>
            ))}
            <div style={{ paddingTop: 18 }}>
              <h2 style={{ fontSize: "1rem", marginBottom: 4 }}><Bell size={15} /> External channels</h2>
              <p className="muted">Slack, Teams, and WhatsApp are unavailable until enabled by your organization.</p>
            </div>
            <button className="btn btn-accent" type="submit" disabled={saving} style={{ marginTop: 16 }}>
              {saving ? "Saving…" : "Save preferences"}
            </button>
          </form>
        )}
      </main>
    </div>
  );
}
