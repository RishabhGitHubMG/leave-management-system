import React from "react";

/**
 * SettribeLogo Component
 * 
 * High-fidelity vector SVG representation of the official Settribe brand logo:
 * - "SETTribe" (bold 'SET' + medium 'Tribe')
 * - Signature lime-to-yellow-green accent gradient bar
 * - "An Ed-Tech Prudentia" tagline with corporate tracking
 * 
 * @param {Object} props
 * @param {string} [props.className] - Optional extra CSS class
 * @param {boolean} [props.showTagline=true] - Whether to show "An Ed-Tech Prudentia"
 * @param {"auto"|"light"|"dark"} [props.mode="auto"] - Color mode for text
 * @param {number} [props.height=44] - SVG height in pixels
 */
export default function SettribeLogo({
  className = "",
  showTagline = true,
  mode = "auto",
  height = 42,
}) {
  // Determine primary text color based on mode
  const textColor = mode === "light" ? "#ffffff" : mode === "dark" ? "#0f2b48" : "currentColor";
  const taglineColor = mode === "light" ? "#94a3b8" : mode === "dark" ? "#1e3a5f" : "var(--brand-navy, #0f2b48)";

  return (
    <div className={`settribe-logo-wrap ${className}`} style={{ display: "inline-flex", alignItems: "center" }}>
      <svg
        height={height}
        viewBox="0 0 320 86"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        role="img"
        aria-label="Settribe - An Ed-Tech Prudentia"
      >
        <defs>
          <linearGradient id="settribeGradient" x1="0" y1="0" x2="320" y2="0" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#84cc16" />
            <stop offset="40%" stopColor="#a3e635" />
            <stop offset="75%" stopColor="#84cc16" />
            <stop offset="100%" stopColor="#eab308" />
          </linearGradient>
        </defs>

        {/* SETTribe Wordmark */}
        <text
          x="4"
          y="46"
          fontFamily="'Plus Jakarta Sans', system-ui, -apple-system, sans-serif"
          letterSpacing="-0.5px"
        >
          <tspan fontWeight="900" fontSize="48" fill={textColor}>SET</tspan>
          <tspan fontWeight="500" fontSize="48" fill={textColor}>Tribe</tspan>
        </text>

        {/* Signature Accent Line */}
        <rect
          x="4"
          y="56"
          width="310"
          height="5.5"
          rx="2.75"
          fill="url(#settribeGradient)"
        />

        {/* Tagline */}
        {showTagline && (
          <text
            x="8"
            y="78"
            fontFamily="'Plus Jakarta Sans', system-ui, -apple-system, sans-serif"
            fontWeight="700"
            fontSize="15"
            letterSpacing="5.8px"
            fill={taglineColor}
          >
            An Ed-Tech Prudentia
          </text>
        )}
      </svg>
    </div>
  );
}
