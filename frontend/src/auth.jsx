import { createContext, useContext, useState } from "react";
import { useNavigate } from "react-router-dom";

/**
 * React Context for globally tracking and providing authentication state.
 */
const AuthContext = createContext(null);

/**
 * Custom hook to easily consume authentication context in any React component.
 * Exposes: { user, login, logout }
 */
export const useAuth = () => useContext(AuthContext);

/**
 * AuthProvider component that wraps the application.
 * Manages user profile state and synchronizes JWT token with localStorage.
 * Extended to persist rich profile fields (department, designation, phoneNumber, employeeId).
 */
export function AuthProvider({ children }) {
  // Initialize user state from localStorage if a previous session exists
  const [user, setUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem("user"));
    } catch {
      return null;
    }
  });

  /**
   * Sets the authenticated user session:
   * - Saves the JWT token to localStorage for subsequent Axios requests.
   * - Saves full user profile (id, name, email, role + extended profile) to localStorage.
   */
  const login = (data) => {
    const u = {
      id: data.id,
      name: data.name,
      email: data.email,
      role: data.role,
      // Extended profile fields — may be null for legacy accounts
      department: data.department || null,
      designation: data.designation || null,
      phoneNumber: data.phoneNumber || null,
      employeeId: data.employeeId || null,
    };
    localStorage.setItem("token", data.token);
    localStorage.setItem("user", JSON.stringify(u));
    setUser(u);
  };

  /**
   * Logs out the user on the client side:
   * - Clears the JWT token and user profile from localStorage.
   * - Sets user state to null, immediately redirecting to login.
   * - Passes a logout notice message via router state for display on the login page.
   */
  const logout = (navigate) => {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    setUser(null);
    if (navigate) {
      navigate("/login", {
        state: { notice: "You have been successfully signed out. Thank you for using Settribe." },
      });
    }
  };

  return <AuthContext.Provider value={{ user, login, logout }}>{children}</AuthContext.Provider>;
}
