import { useState } from "react";
import { X } from "lucide-react";
import api, { errMsg } from "../api";

export default function ProfileForm({ user, onSave, onCancel }) {
  const [name, setName] = useState(user.name || "");
  const [phoneNumber, setPhoneNumber] = useState(user.phoneNumber || "");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    const normalizedName = name.trim();
    if (normalizedName.length < 2) {
      setError("Name must be at least 2 characters.");
      return;
    }
    setSaving(true);
    setError("");
    const requestToken = localStorage.getItem("token");
    try {
      const { data } = await api.put("/users/me", {
        name: normalizedName,
        phoneNumber: phoneNumber.trim(),
      });
      if (localStorage.getItem("token") !== requestToken) return;
      onSave(data);
    } catch (requestError) {
      setError(errMsg(requestError));
    } finally {
      setSaving(false);
    }
  };


  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="profile-modal-title"
      onClick={(event) => {
        if (event.target === event.currentTarget && !saving) onCancel();
      }}
    >
      <div className="modal-dialog">
        <div className="modal-header">
          <h2 id="profile-modal-title">Edit Profile</h2>
          <button type="button" className="modal-close-btn" onClick={onCancel} disabled={saving} aria-label="Close dialog">
            <X size={16} />
          </button>
        </div>
        <form onSubmit={submit}>
          <div className="modal-body">
            {error && <div className="banner error" role="alert">{error}</div>}
            <div className="form-group">
              <label className="form-label" htmlFor="profile-name">Name</label>
              <input
                id="profile-name"
                className="form-input"
                value={name}
                onChange={(event) => setName(event.target.value)}
                maxLength={100}
                required
                disabled={saving}
              />
            </div>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" htmlFor="profile-phone">Phone number</label>
              <input
                id="profile-phone"
                className="form-input"
                type="tel"
                value={phoneNumber}
                onChange={(event) => setPhoneNumber(event.target.value)}
                maxLength={25}
                disabled={saving}
              />
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={saving}>Cancel</button>
            <button type="submit" className="btn btn-accent" disabled={saving}>{saving ? "Saving..." : "Save profile"}</button>
          </div>
        </form>
      </div>
    </div>
  );
}
