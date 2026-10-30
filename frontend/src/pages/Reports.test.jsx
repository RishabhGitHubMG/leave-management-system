import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import api from "../api";
import Reports from "./Reports";

vi.mock("../api", () => ({
  default: { get: vi.fn() },
  errMsg: (error) => error.response?.data?.message || error.message,
}));

vi.mock("../auth", () => ({
  useAuth: () => ({ user: { id: 7, name: "Employee", email: "employee@example.com", role: "EMPLOYEE" } }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Reports", () => {
  it("shows own report rows without employee or department selectors", async () => {
    api.get.mockImplementation((path) => {
      if (path === "/reports/leaves") return Promise.resolve({ data: {
        content: [{ id: 1, userName: "Employee", userEmail: "employee@example.com", leaveType: "ANNUAL", fromDate: "2026-04-01", toDate: "2026-04-02", status: "APPROVED", createdAt: "2026-03-01T10:00:00" }],
        number: 0, totalPages: 1, totalElements: 1, first: true, last: true,
      } });
      return Promise.resolve({ data: { count: 0 } });
    });

    render(<MemoryRouter initialEntries={["/reports"]}><Routes><Route path="/reports" element={<Reports />} /></Routes></MemoryRouter>);

    expect(await screen.findByText("employee@example.com")).toBeTruthy();
    expect(screen.queryByLabelText("Department")).toBeNull();
    expect(screen.queryByLabelText("Employee record ID")).toBeNull();
    await waitFor(() => expect(api.get).toHaveBeenCalledWith("/reports/leaves", {
      params: { page: 0, size: 25 },
    }));
  });
});
