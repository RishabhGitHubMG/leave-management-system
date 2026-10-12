import { createContext, useContext, useState } from "react";

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
   * - Saves user profile (id, name, email, role) to localStorage and component state.
   */
  const login = (data) => {
    const u = { id: data.id, name: data.name, email: data.email, role: data.role };
    localStorage.setItem("token", data.token);
    localStorage.setItem("user", JSON.stringify(u));
    setUser(u);
  };

  /**
   * Logs out the user on the client side:
   * - Clears the JWT token and user profile from localStorage.
   * - Sets user state to null, immediately redirecting to login.
   */
  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    setUser(null);
  };

  return <AuthContext.Provider value={{ user, login, logout }}>{children}</AuthContext.Provider>;
}

