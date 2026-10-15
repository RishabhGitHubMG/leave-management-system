import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import AuthPage from "./AuthPage";

vi.mock("../api", () => ({
  default: { post: vi.fn() },
  errMsg: (error) => error.response?.data?.message || error.message,
}));

vi.mock("../auth", () => ({
  useAuth: () => ({ login: vi.fn() }),
}));

function renderPage(mode) {
  return render(
    <MemoryRouter>
      <AuthPage mode={mode} />
    </MemoryRouter>
  );
}

afterEach(cleanup);

describe("AuthPage", () => {
  it("strips digits from the full name and explains why", () => {
    renderPage("register");
    const nameInput = screen.getByLabelText(/Full Name/);

    fireEvent.change(nameInput, { target: { value: "Jane2" } });

    expect(nameInput.value).toBe("Jane");
    expect(screen.getByText(/Numbers are not allowed in the name field/)).toBeTruthy();
  });

  it("keeps the password visibility toggle reachable by keyboard", () => {
    renderPage("login");
    const toggle = screen.getByRole("button", { name: "Show password" });

    expect(toggle.getAttribute("tabindex")).not.toBe("-1");

    fireEvent.click(toggle);

    expect(document.getElementById("auth-password").type).toBe("text");
    expect(screen.getByRole("button", { name: "Hide password" })).toBeTruthy();
  });
});
