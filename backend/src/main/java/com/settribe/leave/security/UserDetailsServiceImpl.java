package com.settribe.leave.security;

import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implementation of Spring Security's UserDetailsService interface.
 * 
 * Bridges our application's User JPA entity to Spring Security's UserDetails model,
 * mapping the user email as the username and prefixing the role with ROLE_ automatically.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads the user by email (treated as the username).
     * 
     * @param email Email address passed from JWT token or login form.
     * @return UserDetails instance containing email, password hash, and granted authorities.
     * @throws UsernameNotFoundException if no user exists with this email.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name()) // Spring automatically prefixes with "ROLE_"
                .build();
    }
}

