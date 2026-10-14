import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup } from "@testing-library/react";
import api from "../api";
import ProfileForm from "./ProfileForm";

vi.mock("../api", () => ({
  default: { put: vi.fn() },
  errMsg: (error) => error.message,
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ProfileForm", () => {
  it("saves editable profile fields and returns the updated profile", async () => {
    const profile = { id: 1, name: "Alex Updated", phoneNumber: "+1 555 0100" };
    const onSave = vi.fn();
    api.put.mockResolvedValue({ data: profile });
    render(
      <ProfileForm
        user={{ name: "Alex", phoneNumber: "" }}
        onSave={onSave}
        onCancel={vi.fn()}
      />
    );

    fireEvent.change(screen.getByLabelText("Name"), { target: { value: " Alex Updated " } });
    fireEvent.change(screen.getByLabelText("Phone number"), { target: { value: "+1 555 0100" } });
    fireEvent.click(screen.getByRole("button", { name: "Save profile" }));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/users/me", {
      name: "Alex Updated",
      phoneNumber: "+1 555 0100",
    }));
    expect(onSave).toHaveBeenCalledWith(profile);
  });

  it("ignores a profile response after the session token changes", async () => {
    localStorage.setItem("token", "old-token");
    let resolveRequest;
    api.put.mockReturnValue(new Promise((resolve) => { resolveRequest = resolve; }));
    const onSave = vi.fn();
    render(
      <ProfileForm
        user={{ id: 1, name: "Alex", phoneNumber: "" }}
        onSave={onSave}
        onCancel={vi.fn()}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: "Save profile" }));
    await waitFor(() => expect(api.put).toHaveBeenCalled());
    localStorage.setItem("token", "new-token");

    await act(async () => {
      resolveRequest({ data: { id: 1, name: "Alex Updated", phoneNumber: null } });
    });

    expect(onSave).not.toHaveBeenCalled();
  });
});
