import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import api from "../api";
import NotificationBell from "./NotificationBell";

vi.mock("../api", () => ({
  default: { get: vi.fn(), put: vi.fn() },
  errMsg: (error) => error.response?.data?.message || error.message,
}));

const notifications = [
  { id: 2, message: "Your annual leave from Apr 1 to Apr 2 was approved.", read: false, createdAt: "2026-04-01T10:00:00" },
  { id: 1, message: "Your sick leave from Mar 3 to Mar 3 was rejected.", read: true, createdAt: "2026-03-02T09:00:00" },
];

function mockApi({ count = 0, list = notifications } = {}) {
  api.get.mockImplementation((path) => {
    if (path === "/notifications/unread-count") return Promise.resolve({ data: { count } });
    if (path === "/notifications") return Promise.resolve({ data: list });
    return Promise.reject(new Error(`unexpected GET ${path}`));
  });
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("NotificationBell", () => {
  it("shows the unread count on the bell", async () => {
    mockApi({ count: 3 });
    render(<NotificationBell />);

    expect(await screen.findByRole("button", { name: "Notifications, 3 unread" })).toBeTruthy();
    expect(screen.getByText("3")).toBeTruthy();
  });

  it("hides the badge when nothing is unread", async () => {
    mockApi({ count: 0 });
    render(<NotificationBell />);

    await waitFor(() => expect(api.get).toHaveBeenCalledWith("/notifications/unread-count"));
    expect(screen.getByRole("button", { name: "Notifications" })).toBeTruthy();
    expect(screen.queryByText("0")).toBeNull();
  });

  it("caps the badge text at 99+", async () => {
    mockApi({ count: 120 });
    render(<NotificationBell />);

    expect(await screen.findByText("99+")).toBeTruthy();
  });

  it("lists notifications when opened", async () => {
    mockApi({ count: 1 });
    render(<NotificationBell />);

    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 1 unread" }));

    const panel = await screen.findByRole("region", { name: "Notifications" });
    expect(within(panel).getByText(notifications[0].message)).toBeTruthy();
    expect(within(panel).getByText(notifications[1].message)).toBeTruthy();
    expect(api.get).toHaveBeenCalledWith("/notifications");
  });

  it("marks a single notification as read and decrements the badge", async () => {
    mockApi({ count: 1 });
    api.put.mockResolvedValue({ data: {} });
    render(<NotificationBell />);

    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 1 unread" }));
    fireEvent.click(await screen.findByText(notifications[0].message));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/notifications/2/read"));
    expect(screen.getByRole("button", { name: "Notifications" })).toBeTruthy();
  });

  it("marks all notifications as read", async () => {
    mockApi({ count: 2 });
    api.put.mockResolvedValue({ data: {} });
    render(<NotificationBell />);

    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 2 unread" }));
    fireEvent.click(await screen.findByRole("button", { name: /Mark all as read/ }));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/notifications/read-all"));
    expect(screen.queryByRole("button", { name: /Mark all as read/ })).toBeNull();
    expect(screen.getByRole("button", { name: "Notifications" })).toBeTruthy();
  });

  it("closes the dropdown on Escape", async () => {
    mockApi({ count: 0 });
    render(<NotificationBell />);

    fireEvent.click(screen.getByRole("button", { name: "Notifications" }));
    expect(await screen.findByRole("region", { name: "Notifications" })).toBeTruthy();

    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("region", { name: "Notifications" })).toBeNull();
  });
});
