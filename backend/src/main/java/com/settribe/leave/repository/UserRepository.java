package com.settribe.leave.repository;

import com.settribe.leave.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA Repository for User entity database operations.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by unique email address.
     * Spring Data JPA automatically derives the query: SELECT * FROM users WHERE email = ?
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if any account is registered with the given email address.
     * Spring Data JPA derives: SELECT count(*) > 0 FROM users WHERE email = ?
     */
    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    boolean existsByManager_Id(Long managerId);
}
