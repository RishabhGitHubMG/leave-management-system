import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import api from "../api";
import AdminUsers from "./AdminUsers";

vi.mock("../api", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
  errMsg: (error) => error.response?.data?.message || error.message,
}));

vi.mock("../auth", () => ({
  useAuth: () => ({ user: { id: 1, name: "Admin", role: "ADMIN" } }),
}));

const existingUser = {
  id: 2,
  name: "Alex Employee",
  email: "alex@example.com",
  role: "EMPLOYEE",
  department: "Product",
  designation: "Designer",
  phoneNumber: "+1 555 0100",
  employeeId: "E-2",
  managerId: null,
  joiningDate: "2025-01-10",
  employmentType: "FULL_TIME",
  active: true,
};

function renderAdminUsers() {
  return render(
    <MemoryRouter>
      <AdminUsers />
    </MemoryRouter>
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("AdminUsers", () => {
  it("includes phone and employee ID when creating an account", async () => {
    api.get.mockResolvedValue({ data: [] });
    api.post.mockResolvedValue({ data: existingUser });
    renderAdminUsers();

    fireEvent.click(screen.getByRole("button", { name: "Add user" }));
    fireEvent.change(screen.getByLabelText("Full name"), { target: { value: "Alex Employee" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "alex@example.com" } });
    fireEvent.change(screen.getByLabelText(/Initial password/), { target: { value: "password123" } });
    fireEvent.change(screen.getByLabelText("Phone number"), { target: { value: "+1 555 0100" } });
    fireEvent.change(screen.getByLabelText("Employee ID"), { target: { value: "E-2" } });
    fireEvent.click(screen.getByRole("button", { name: "Create account" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/admin/users", expect.objectContaining({
      phoneNumber: "+1 555 0100",
      employeeId: "E-2",
    })));
  });

  it("updates the additional supported user profile fields", async () => {
    api.get.mockResolvedValue({ data: [existingUser] });
    api.put.mockImplementation(async (_path, body) => ({ data: { ...existingUser, ...body } }));
    renderAdminUsers();

    fireEvent.click(await screen.findByRole("button", { name: "Edit details" }));
    fireEvent.change(screen.getByLabelText("Designation"), { target: { value: "Senior Designer" } });
    fireEvent.change(screen.getByLabelText("Employment type"), { target: { value: "INTERN" } });
    fireEvent.change(screen.getByLabelText("Joining date"), { target: { value: "2025-02-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Save details" }));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/admin/users/2", expect.objectContaining({
      designation: "Senior Designer",
      employmentType: "INTERN",
      joiningDate: "2025-02-01",
    })));
  });
});
