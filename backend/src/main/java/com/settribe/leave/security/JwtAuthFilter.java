package com.settribe.leave.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that intercepts incoming HTTP requests to validate JWT Bearer tokens.
 * 
 * Extends OncePerRequestFilter to guarantee single execution per request dispatch.
 * 
 * Note: Intentionally NOT annotated with @Component so it only executes within
 * the Spring Security filter chain configured in SecurityConfig, rather than being
 * auto-registered as a generic servlet filter.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Read the Authorization header
        String header = request.getHeader("Authorization");

        // Check if header exists and starts with "Bearer " prefix
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7); // Strip "Bearer " prefix

            // Validate token signature and expiration
            if (jwtUtil.isValid(token)) {
                try {
                    // Extract user email and load fresh user details from the database.
                    // Reloading from DB ensures disabled accounts and role updates take effect immediately.
                    UserDetails user = userDetailsService.loadUserByUsername(jwtUtil.extractEmail(token));

                    // Build Spring Security Authentication token
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Store authenticated user in the security context for this thread
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (UsernameNotFoundException ignored) {
                    // If user was deleted from DB, leave unauthenticated -> triggers 401 Unauthorized
                }
            }
        }

        // Continue filter chain execution
        chain.doFilter(request, response);
    }
}

