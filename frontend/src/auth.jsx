import { createContext, useContext, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "./api";

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
  const sessionVersion = useRef(0);

  useEffect(() => {
    let checking = false;
    const refreshUser = async () => {
      if (checking) return;
      const token = localStorage.getItem("token");
      if (!token) {
        setUser(null);
        return;
      }
      const requestSessionVersion = sessionVersion.current;
      checking = true;
      try {
        const { data } = await api.get("/users/me");
        if (sessionVersion.current !== requestSessionVersion || localStorage.getItem("token") !== token) return;
        const currentUser = {
          id: data.id,
          name: data.name,
          email: data.email,
          role: data.role,
          department: data.department || null,
          designation: data.designation || null,
          phoneNumber: data.phoneNumber || null,
          employeeId: data.employeeId || null,
        };
        localStorage.setItem("user", JSON.stringify(currentUser));
        setUser(currentUser);
      } catch (error) {
        if (
          sessionVersion.current === requestSessionVersion &&
          localStorage.getItem("token") === token &&
          error.response?.status === 401
        ) {
          localStorage.removeItem("token");
          localStorage.removeItem("user");
          setUser(null);
        }
      } finally {
        checking = false;
      }
    };
    const refreshWhenVisible = () => {
      if (document.visibilityState === "visible") refreshUser();
    };
    refreshUser();
    window.addEventListener("focus", refreshUser);
    document.addEventListener("visibilitychange", refreshWhenVisible);
    return () => {
      window.removeEventListener("focus", refreshUser);
      document.removeEventListener("visibilitychange", refreshWhenVisible);
    };
  }, []);

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
    sessionVersion.current += 1;
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
  const syncUserProfile = (profile) => {
    if (!user || user.id !== profile.id) return;
    const updatedUser = {
      ...user,
      name: profile.name,
      phoneNumber: profile.phoneNumber || null,
    };
    localStorage.setItem("user", JSON.stringify(updatedUser));
    setUser(updatedUser);
  };

  const logout = (navigate) => {
    sessionVersion.current += 1;
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    setUser(null);
    if (navigate) {
      navigate("/login", {
        state: { notice: "You have been successfully signed out. Thank you for using Settribe." },
      });
    }
  };

  return <AuthContext.Provider value={{ user, login, logout, syncUserProfile }}>{children}</AuthContext.Provider>;
}
