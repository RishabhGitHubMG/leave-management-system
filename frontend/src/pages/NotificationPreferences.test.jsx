import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import api from "../api";
import NotificationPreferences from "./NotificationPreferences";

vi.mock("../api", () => ({
  default: { get: vi.fn(), put: vi.fn() },
  errMsg: (error) => error.response?.data?.message || error.message,
}));

const preferences = {
  inAppEnabled: true,
  emailEnabled: true,
  dailyDigestEnabled: true,
  approvalRemindersEnabled: true,
  slackEnabled: false,
  teamsEnabled: false,
  whatsappEnabled: false,
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("NotificationPreferences", () => {
  it("loads, updates, and persists the authenticated user's preferences", async () => {
    api.get.mockImplementation((path) => {
      if (path === "/notifications/preferences") return Promise.resolve({ data: preferences });
      if (path === "/notifications/unread-count") return Promise.resolve({ data: { count: 0 } });
      return Promise.resolve({ data: { content: [], last: true } });
    });
    api.put.mockImplementation((path, body) => Promise.resolve({ data: body }));

    render(<MemoryRouter><NotificationPreferences /></MemoryRouter>);
    const email = await screen.findByRole("checkbox", { name: /Email notifications/ });
    fireEvent.click(email);
    fireEvent.click(screen.getByRole("button", { name: "Save preferences" }));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/notifications/preferences", {
      ...preferences,
      emailEnabled: false,
    }));
    expect(await screen.findByRole("status")).toBeTruthy();
  });
});
