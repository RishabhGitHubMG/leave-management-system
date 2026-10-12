import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./auth";
import "./styles.css";

/**
 * React Application Root Entry:
 * Mounts the React component tree into the DOM root container.
 * 
 * Provider Hierarchy:
 * 1. React.StrictMode: Highlights potential bugs during development.
 * 2. BrowserRouter: Enables client-side URL routing and browser history navigation.
 * 3. AuthProvider: Injects global authentication state across the entire application.
 * 4. App: Main routing and view component.
 */
ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>
);

