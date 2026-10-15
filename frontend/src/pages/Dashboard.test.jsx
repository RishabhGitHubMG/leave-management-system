import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import api from "../api";
import Dashboard from "./Dashboard";

vi.mock("../api", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  errMsg: (error) => error.response?.data?.message || error.message,
}));

vi.mock("../auth", () => ({
  useAuth: () => ({
    user: { id: 1, name: "Admin User", email: "admin@example.com", role: "ADMIN" },
    logout: vi.fn(),
    syncUserProfile: vi.fn(),
  }),
}));

const pendingLeave = {
  id: 42,
  userId: 2,
  userName: "Alex Employee",
  userEmail: "alex@example.com",
  userDepartment: "Product",
  userDesignation: "Designer",
  userEmployeeId: "E-2",
  leaveType: "ANNUAL",
  fromDate: "2026-04-10",
  toDate: "2026-04-11",
  reason: "Family event",
  status: "PENDING",
  adminRemarks: null,
  createdAt: "2026-04-01T10:00:00",
};

const approvedLeave = {
  ...pendingLeave,
  id: 41,
  fromDate: "2026-04-01",
  toDate: "2026-04-04",
  status: "APPROVED",
};

function renderDashboard() {
  return render(
    <MemoryRouter>
      <Dashboard />
    </MemoryRouter>
  );
}

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.clearAllMocks();
});

describe("Dashboard integration", () => {
  it("keeps estimated balances based on the full history when request filters change", async () => {
    api.get.mockImplementation((path, config = {}) => {
      if (path !== "/leaves") return Promise.resolve({ data: [] });
      if (config.params?.status === "PENDING") return Promise.resolve({ data: [pendingLeave] });
      return Promise.resolve({ data: [approvedLeave, pendingLeave] });
    });
    renderDashboard();

    const annualBalance = await screen.findByLabelText("Annual Leave balance");
    expect(within(annualBalance).getByText("4 days used · 14 remaining")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Toggle filters" }));
    fireEvent.click(screen.getByRole("button", { name: "Pending", exact: true }));

    await waitFor(() => expect(api.get).toHaveBeenCalledWith("/leaves", { params: { status: "PENDING" } }));
    await waitFor(() => expect(api.get).toHaveBeenCalledWith("/leaves"));
    expect(within(annualBalance).getByText("4 days used · 14 remaining")).toBeTruthy();
  });

  it("sends optional approval remarks to the backend", async () => {
    let decided = false;
    api.get.mockImplementation((path) => Promise.resolve({
      data: path === "/admin/leaves" && !decided ? [pendingLeave] : [],
    }));
    api.put.mockImplementation(async (path, body) => {
      decided = true;
      return { data: { ...pendingLeave, ...body } };
    });
    renderDashboard();

    fireEvent.click(await screen.findByRole("tab", { name: "All Requests" }));
    fireEvent.click(await screen.findByRole("button", { name: "Approve Alex Employee's request" }));
    fireEvent.change(screen.getByLabelText(/Approval remarks/), {
      target: { value: "Approved after coverage review" },
    });

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Confirm Approval" }));
    });

    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/admin/leaves/42/status", {
      status: "APPROVED",
      adminRemarks: "Approved after coverage review",
    }));
  });

  it("keeps KPI totals based on the full history when a status filter is applied", async () => {
    api.get.mockImplementation((path, config = {}) => {
      if (path !== "/leaves") return Promise.resolve({ data: [] });
      if (config.params?.status === "PENDING") return Promise.resolve({ data: [pendingLeave] });
      return Promise.resolve({ data: [approvedLeave, pendingLeave] });
    });
    renderDashboard();

    const totalCard = await screen.findByLabelText("Total Requests");
    await waitFor(() => expect(within(totalCard).getByText("2")).toBeTruthy());

    fireEvent.click(screen.getByRole("button", { name: "Toggle filters" }));
    fireEvent.click(screen.getByRole("button", { name: "Pending", exact: true }));

    await waitFor(() => expect(api.get).toHaveBeenCalledWith("/leaves", { params: { status: "PENDING" } }));
    await waitFor(() => expect(within(screen.getByLabelText("Pending Review")).getByText("1")).toBeTruthy());
    expect(within(totalCard).getByText("2")).toBeTruthy();
  });
});
