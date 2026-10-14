import React, { useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { ArrowLeft, UserPlus, AlertTriangle, CheckCircle2 } from "lucide-react";
import api, { errMsg } from "../api";
import { useAuth } from "../auth";
import SettribeLogo from "../components/SettribeLogo";

const ROLES = ["EMPLOYEE", "MANAGER", "HR", "ADMIN"];
const EMPLOYMENT_TYPES = ["FULL_TIME", "INTERN", "CONTRACT"];

const pretty = (s) => (s ? s.toLowerCase().replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase()) : "");

const EMPTY_FORM = {
  name: "",
  email: "",
  password: "",
  role: "EMPLOYEE",
  managerId: "",
  department: "",
  designation: "",
  phoneNumber: "",
  employeeId: "",
  employmentType: "FULL_TIME",
  joiningDate: "",
};

export default function AdminUsers() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user?.role === "ADMIN";

  const [users, setUsers] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busyId, setBusyId] = useState(null);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [creating, setCreating] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [editForm, setEditForm] = useState(null);
  const [savingDetails, setSavingDetails] = useState(false);

  useEffect(() => {
    if (!isAdmin) return;
    api
      .get("/admin/users")
      .then(({ data }) => setUsers(data))
      .catch((e) => setError(errMsg(e)));
  }, [isAdmin]);

  if (!isAdmin) return <Navigate to="/dashboard" replace />;

  const applyUpdate = async (id, body, message) => {
    setBusyId(id);
    setError("");
    setNotice("");
    try {
      const { data } = await api.put(`/admin/users/${id}`, body);
      setUsers((list) => list.map((u) => (u.id === id ? data : u)));
      setNotice(message);
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setBusyId(null);
    }
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    setCreating(true);
    setError("");
    setNotice("");
    try {
      const payload = {
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
        department: form.department.trim() || null,
        designation: form.designation.trim() || null,
        phoneNumber: form.phoneNumber.trim() || null,
        employeeId: form.employeeId.trim() || null,
        managerId: form.managerId ? Number(form.managerId) : null,
        employmentType: form.employmentType,
        joiningDate: form.joiningDate || null,
      };
      const { data } = await api.post("/admin/users", payload);
      setUsers((list) => [...list, data].sort((a, b) => a.name.localeCompare(b.name)));
      setForm(EMPTY_FORM);
      setShowCreate(false);
      setNotice(`Account created for ${data.name}.`);
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setCreating(false);
    }
  };

  const setField = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  const setEditField = (key) => (e) => setEditForm((f) => ({ ...f, [key]: e.target.value }));

  const openDetails = (user) => {
    setEditingUser(user);
    setEditForm({
      name: user.name || "",
      department: user.department || "",
      designation: user.designation || "",
      phoneNumber: user.phoneNumber || "",
      joiningDate: user.joiningDate || "",
      employmentType: user.employmentType || "",
    });
  };

  const handleUpdateDetails = async (event) => {
    event.preventDefault();
    if (!editingUser || !editForm) return;
    const normalizedName = editForm.name.trim();
    if (normalizedName.length < 2) {
      setError("Name must be at least 2 characters.");
      return;
    }
    setSavingDetails(true);
    setError("");
    setNotice("");
    try {
      const { data } = await api.put(`/admin/users/${editingUser.id}`, {
        name: normalizedName,
        department: editForm.department.trim(),
        designation: editForm.designation.trim(),
        phoneNumber: editForm.phoneNumber.trim(),
        joiningDate: editForm.joiningDate || null,
        employmentType: editForm.employmentType || undefined,
      });
      setUsers((list) => list.map((user) => (user.id === data.id ? data : user)));
      setEditingUser(null);
      setEditForm(null);
      setNotice(`Profile updated for ${data.name}.`);
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setSavingDetails(false);
    }
  };

  return (
    <div className="dashboard-page">
      <nav className="site-nav" aria-label="Main navigation">
        <div className="nav-container">
          <Link to="/" className="nav-brand" aria-label="Go to Settribe home">
            <SettribeLogo height={34} />
          </Link>
        </div>
      </nav>

      <main className="dashboard-container">
        {notice && (
          <div className="banner ok" role="status">
            <CheckCircle2 size={16} />
            <div style={{ flex: 1 }}>{notice}</div>
          </div>
        )}
        {error && (
          <div className="banner error" role="alert">
            <AlertTriangle size={16} />
            <div style={{ flex: 1 }}>{error}</div>
          </div>
        )}

        <header className="dashboard-header">
          <div className="dashboard-title-area">
            <h1>User Management</h1>
            <p className="muted">Create accounts, assign roles and managers, and deactivate people who leave.</p>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button className="btn btn-secondary" onClick={() => navigate("/dashboard")}>
              <ArrowLeft size={14} /> Dashboard
            </button>
            <button className="btn btn-accent" onClick={() => setShowCreate((s) => !s)}>
              <UserPlus size={14} /> {showCreate ? "Close" : "Add user"}
            </button>
          </div>
        </header>

        {showCreate && (
          <form className="balance-card" onSubmit={handleCreate} aria-label="Create user">
            <div className="form-row-2">
              <div className="form-group">
                <label className="form-label" htmlFor="new-name">Full name</label>
                <input id="new-name" className="form-input" required minLength={2} value={form.name} onChange={setField("name")} />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="new-email">Email</label>
                <input id="new-email" type="email" className="form-input" required value={form.email} onChange={setField("email")} />
              </div>
            </div>
            <div className="form-row-2">
              <div className="form-group">
                <label className="form-label" htmlFor="new-password">Initial password (min 8 characters)</label>
                <input id="new-password" type="password" className="form-input" required minLength={8} value={form.password} onChange={setField("password")} />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="new-role">Role</label>
                <select id="new-role" className="form-select" value={form.role} onChange={setField("role")}>
                  {ROLES.map((r) => <option key={r} value={r}>{pretty(r)}</option>)}
                </select>
              </div>
            </div>
            <div className="form-row-2">
              <div className="form-group">
                <label className="form-label" htmlFor="new-manager">Manager</label>
                <select id="new-manager" className="form-select" value={form.managerId} onChange={setField("managerId")}>
                  <option value="">No manager</option>
                  {users.filter((u) => u.active && u.role !== "EMPLOYEE").map((u) => (
                    <option key={u.id} value={u.id}>{u.name}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="new-employment">Employment type</label>
                <select id="new-employment" className="form-select" value={form.employmentType} onChange={setField("employmentType")}>
                  {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{pretty(t)}</option>)}
                </select>
              </div>
            </div>
            <div className="form-row-2">
              <div className="form-group">
                <label className="form-label" htmlFor="new-department">Department</label>
                <input id="new-department" className="form-input" value={form.department} onChange={setField("department")} />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="new-designation">Designation</label>
                <input id="new-designation" className="form-input" value={form.designation} onChange={setField("designation")} />
              </div>
            </div>
            <div className="form-row-2">
              <div className="form-group">
                <label className="form-label" htmlFor="new-phone">Phone number</label>
                <input id="new-phone" type="tel" className="form-input" maxLength={25} value={form.phoneNumber} onChange={setField("phoneNumber")} />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="new-employee-id">Employee ID</label>
                <input id="new-employee-id" className="form-input" maxLength={50} value={form.employeeId} onChange={setField("employeeId")} />
              </div>
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="new-joining">Joining date</label>
              <input id="new-joining" type="date" className="form-input" value={form.joiningDate} onChange={setField("joiningDate")} />
            </div>
            <button type="submit" className="btn btn-accent" disabled={creating}>
              {creating ? "Creating..." : "Create account"}
            </button>
          </form>
        )}

        <div style={{ overflowX: "auto" }}>
          <table className="users-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Manager</th>
                <th>Department</th>
                <th>Status</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const self = u.id === user?.id;
                const editing = editingUser?.id === u.id;
                const busy = busyId === u.id || (savingDetails && editing);
                const managerOptions = users.filter(
                  (m) => m.id !== u.id && (m.id === u.managerId || (m.active && m.role !== "EMPLOYEE"))
                );
                return (
                  <React.Fragment key={u.id}>
                    <tr>
                    <td>
                      {u.name}
                      {u.employeeId && <div className="muted">{u.employeeId}</div>}
                    </td>
                    <td>{u.email}</td>
                    <td>
                      <select
                        className="form-select"
                        aria-label={`Role for ${u.name}`}
                        value={u.role}
                        disabled={self || busy || editing}
                        onChange={(e) => applyUpdate(u.id, { role: e.target.value }, `Role updated for ${u.name}.`)}
                      >
                        {ROLES.map((r) => <option key={r} value={r}>{pretty(r)}</option>)}
                      </select>
                    </td>
                    <td>
                      <select
                        className="form-select"
                        aria-label={`Manager for ${u.name}`}
                        value={u.managerId ?? ""}
                        disabled={busy || editing}
                        onChange={(e) =>
                          applyUpdate(
                            u.id,
                            { managerId: e.target.value ? Number(e.target.value) : 0 },
                            `Manager updated for ${u.name}.`
                          )
                        }
                      >
                        <option value="">No manager</option>
                        {managerOptions.map((m) => (
                          <option key={m.id} value={m.id}>{m.name}</option>
                        ))}
                      </select>
                    </td>
                    <td>{u.department || "-"}</td>
                    <td>
                      <button
                        className={`btn btn-sm ${u.active ? "btn-secondary" : "btn-accent"}`}
                        disabled={self || busy || editing}
                        onClick={() =>
                          applyUpdate(
                            u.id,
                            { active: !u.active },
                            `${u.name} ${u.active ? "deactivated" : "reactivated"}.`
                          )
                        }
                      >
                        {u.active ? "Deactivate" : "Reactivate"}
                      </button>
                    </td>
                    <td>
                      <button className="btn btn-secondary btn-sm" disabled={savingDetails || editing} onClick={() => openDetails(u)}>
                        Edit details
                      </button>
                    </td>
                  </tr>
                  {editingUser?.id === u.id && editForm && (
                    <tr>
                      <td colSpan="7">
                        <form className="balance-card" onSubmit={handleUpdateDetails} aria-label={`Edit details for ${u.name}`}>
                          <div className="form-row-2">
                            <div className="form-group">
                              <label className="form-label" htmlFor={`edit-name-${u.id}`}>Name</label>
                              <input id={`edit-name-${u.id}`} className="form-input" required minLength={2} maxLength={100} value={editForm.name} onChange={setEditField("name")} />
                            </div>
                            <div className="form-group">
                              <label className="form-label" htmlFor={`edit-phone-${u.id}`}>Phone number</label>
                              <input id={`edit-phone-${u.id}`} type="tel" className="form-input" maxLength={25} value={editForm.phoneNumber} onChange={setEditField("phoneNumber")} />
                            </div>
                          </div>
                          <div className="form-row-2">
                            <div className="form-group">
                              <label className="form-label" htmlFor={`edit-department-${u.id}`}>Department</label>
                              <input id={`edit-department-${u.id}`} className="form-input" maxLength={100} value={editForm.department} onChange={setEditField("department")} />
                            </div>
                            <div className="form-group">
                              <label className="form-label" htmlFor={`edit-designation-${u.id}`}>Designation</label>
                              <input id={`edit-designation-${u.id}`} className="form-input" maxLength={100} value={editForm.designation} onChange={setEditField("designation")} />
                            </div>
                          </div>
                          <div className="form-row-2">
                            <div className="form-group">
                              <label className="form-label" htmlFor={`edit-employment-${u.id}`}>Employment type</label>
                              <select id={`edit-employment-${u.id}`} className="form-select" value={editForm.employmentType} onChange={setEditField("employmentType")}>
                                <option value="" disabled>Select employment type</option>
                                {EMPLOYMENT_TYPES.map((type) => <option key={type} value={type}>{pretty(type)}</option>)}
                              </select>
                            </div>
                            <div className="form-group">
                              <label className="form-label" htmlFor={`edit-joining-${u.id}`}>Joining date</label>
                              <input id={`edit-joining-${u.id}`} type="date" className="form-input" value={editForm.joiningDate} onChange={setEditField("joiningDate")} />
                              <span className="muted">Leave blank to keep the existing date.</span>
                            </div>
                          </div>
                          <button type="submit" className="btn btn-accent" disabled={savingDetails}>
                            {savingDetails ? "Saving..." : "Save details"}
                          </button>
                          <button type="button" className="btn btn-secondary" disabled={savingDetails} onClick={() => { setEditingUser(null); setEditForm(null); }}>
                            Cancel
                          </button>
                        </form>
                      </td>
                    </tr>
                  )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </main>
    </div>
  );
}
