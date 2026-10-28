package com.settribe.leave.security;

import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
     * @param email Email address passed from a login form.
     * @return UserDetails instance containing email, password hash, and granted authorities.
     * @throws UsernameNotFoundException if no user exists with this email.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return toUserDetails(userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found")));
    }

    /**
     * Loads the user referenced by a verified JWT subject.
     *
     * @param id Primary key of the user.
     * @return UserDetails reflecting the current database state of the account.
     * @throws UsernameNotFoundException if no user exists with this id.
     */
    public UserDetails loadUserById(long id) throws UsernameNotFoundException {
        return toUserDetails(userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found")));
    }

    private static UserDetails toUserDetails(User user) {
        LocalDateTime lockedUntil = user.getLockedUntil();
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name()) // Spring automatically prefixes with "ROLE_"
                .disabled(!user.isActive())
                .accountLocked(lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now()))
                .build();
    }
}
