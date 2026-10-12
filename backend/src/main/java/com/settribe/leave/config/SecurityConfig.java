package com.settribe.leave.config;

import com.settribe.leave.security.JwtAuthFilter;
import com.settribe.leave.security.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security configuration for the application.
 * 
 * Key Responsibilities:
 * 1. Configures PasswordEncoder (BCrypt) for hashing passwords securely.
 * 2. Defines HTTP request authorization rules (Public, Authenticated, Admin-only).
 * 3. Enforces stateless session management (JWT tokens, no HTTP sessions).
 * 4. Plugs in the custom JwtAuthFilter before UsernamePasswordAuthenticationFilter.
 * 5. Configures Cross-Origin Resource Sharing (CORS) for React frontend integration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * BCrypt password encoder bean for hashing passwords before saving to DB
     * and verifying passwords during login attempts.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Defines the primary security filter chain pipeline.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtUtil jwtUtil,
                                           UserDetailsService userDetailsService) throws Exception {
        // Instantiate the custom JWT authentication filter
        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtUtil, userDetailsService);

        http
                // Disable CSRF: Not needed because we use stateless JWT authentication (no session cookies)
                .csrf(csrf -> csrf.disable())
                // Enable CORS with the configuration source defined below
                .cors(Customizer.withDefaults())
                // Set session management to STATELESS: Server never stores session state
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Configure endpoint authorization rules:
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints: Registration and Login
                        .requestMatchers("/api/auth/**").permitAll()
                        // Admin-only endpoints: Leave approval and admin listing
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // All other endpoints require authentication (valid JWT token)
                        .anyRequest().authenticated())
                // Custom error responses for unauthenticated (401) and unauthorized (403) requests
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeError(res, 401, "Unauthorized", "Authentication is required or the token is invalid"))
                        .accessDeniedHandler((req, res, e) ->
                                writeError(res, 403, "Forbidden", "You do not have permission to access this resource")))
                // Place our custom JWT filter before the standard username/password authentication filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configures CORS headers so the React frontend can make API calls from allowed origins.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin}") String origin) {
        CorsConfiguration config = new CorsConfiguration();
        if ("*".equals(origin != null ? origin.trim() : "")) {
            // Allow all origins when configured as wildcard
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            // Allow the specific frontend origin (e.g., http://localhost:5173 or Render static site)
            config.setAllowedOrigins(List.of(origin));
        }
        // Allowed HTTP methods for REST operations
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Allowed headers including Authorization (Bearer token) and Content-Type (JSON)
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Helper method to write clean JSON error responses for 401 and 403 HTTP states.
     */
    private static void writeError(HttpServletResponse res, int status, String error, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"status\":" + status + ",\"error\":\"" + error
                + "\",\"message\":\"" + message + "\"}");
    }
}

