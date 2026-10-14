import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "./auth";
import api from "./api";

vi.mock("./api", () => ({ default: { get: vi.fn() } }));

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function Harness() {
  const { user, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="user-id">{user?.id ?? "none"}</span>
      <button onClick={() => logout()}>Logout</button>
      <button onClick={() => login({ id: 2, name: "New", email: "new@example.com", role: "EMPLOYEE", token: "new-token" })}>
        Login as new user
      </button>
    </div>
  );
}

function renderWithSession() {
  localStorage.setItem("token", "old-token");
  localStorage.setItem("user", JSON.stringify({ id: 1, name: "Old", role: "ADMIN" }));
  return render(
    <AuthProvider>
      <Harness />
    </AuthProvider>
  );
}

const oldUserResponse = {
  id: 1,
  name: "Old",
  email: "old@example.com",
  role: "ADMIN",
};

describe("AuthProvider session refresh", () => {
  afterEach(cleanup);

  beforeEach(() => {
    localStorage.clear();
    api.get.mockReset();
  });

  it("does not restore the session when a refresh resolves after logout", async () => {
    const request = deferred();
    api.get.mockReturnValue(request.promise);
    renderWithSession();

    await waitFor(() => expect(api.get).toHaveBeenCalledWith("/users/me"));
    fireEvent.click(screen.getByRole("button", { name: "Logout" }));

    await act(async () => {
      request.resolve({ data: oldUserResponse });
      await request.promise;
    });

    expect(screen.getByTestId("user-id").textContent).toBe("none");
    expect(localStorage.getItem("token")).toBeNull();
    expect(localStorage.getItem("user")).toBeNull();
  });

  it("does not overwrite a newly logged-in account with the old refresh response", async () => {
    const request = deferred();
    api.get.mockReturnValue(request.promise);
    renderWithSession();

    await waitFor(() => expect(api.get).toHaveBeenCalledWith("/users/me"));
    fireEvent.click(screen.getByRole("button", { name: "Login as new user" }));

    await act(async () => {
      request.resolve({ data: oldUserResponse });
      await request.promise;
    });

    expect(screen.getByTestId("user-id").textContent).toBe("2");
    expect(localStorage.getItem("token")).toBe("new-token");
    expect(JSON.parse(localStorage.getItem("user")).id).toBe(2);
  });
});
