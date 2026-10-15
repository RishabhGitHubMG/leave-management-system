package com.settribe.leave.repository;

import com.settribe.leave.entity.LeaveRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for LeaveRequest database operations.
 */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /**
     * Ownership Check:
     * Retrieves a leave request only if it belongs to the given user ID.
     * Returns Optional.empty() if the request belongs to a different user, preventing unauthorized access.
     */
    Optional<LeaveRequest> findByIdAndUserId(Long id, Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LeaveRequest l WHERE l.id = :id AND l.user.id = :userId")
    Optional<LeaveRequest> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Employee View:
     * Retrieves all leave requests for a specific user, sorted newest first.
     * Supports dynamic filtering: passing null for status or type ignores that filter.
     */
    @Query("""
            SELECT l FROM LeaveRequest l
            WHERE l.user.id = :userId
              AND (:status IS NULL OR l.status = :status)
              AND (:type IS NULL OR l.leaveType = :type)
            ORDER BY l.createdAt DESC
            """)
    List<LeaveRequest> findForUser(@Param("userId") Long userId,
                                   @Param("status") LeaveStatus status,
                                   @Param("type") LeaveType type);

    /**
     * Admin View:
     * Retrieves all employee leave requests with optional status and type filters.
     * 
     * Performance Optimization:
     * Uses JOIN FETCH l.user to eagerly load the associated User entity in a single SQL query,
     * completely eliminating the Hibernate N+1 query performance bottleneck.
     */
    /**
     * Admin View:
     * Retrieves all employee leave requests with optional status and type filters.
     * 
     * Performance Optimization:
     * Uses JOIN FETCH l.user to eagerly load the associated User entity in a single SQL query,
     * completely eliminating the Hibernate N+1 query performance bottleneck.
     */
    @Query("""
            SELECT l FROM LeaveRequest l JOIN FETCH l.user
            WHERE (:status IS NULL OR l.status = :status)
              AND (:type IS NULL OR l.leaveType = :type)
            ORDER BY l.createdAt DESC
            """)
    List<LeaveRequest> findAllFiltered(@Param("status") LeaveStatus status,
                                       @Param("type") LeaveType type);

    @Query("""
            SELECT l FROM LeaveRequest l JOIN FETCH l.user u
            WHERE u.manager.id = :managerId
              AND (:status IS NULL OR l.status = :status)
              AND (:type IS NULL OR l.leaveType = :type)
            ORDER BY l.createdAt DESC
            """)
    List<LeaveRequest> findForTeam(@Param("managerId") Long managerId,
                                   @Param("status") LeaveStatus status,
                                   @Param("type") LeaveType type);

    /**
     * Checks if a user already has a leave request with a status outside {@code inactiveStatuses}
     * that overlaps the specified [fromDate, toDate] interval, optionally excluding an existing leave ID (for edit operations).
     */
    @Query("""
            SELECT COUNT(l) > 0 FROM LeaveRequest l
            WHERE l.user.id = :userId
              AND l.status NOT IN :inactiveStatuses
              AND (:excludeId IS NULL OR l.id != :excludeId)
              AND (l.fromDate <= :toDate AND l.toDate >= :fromDate)
            """)
    boolean hasOverlappingLeave(@Param("userId") Long userId,
                                @Param("fromDate") java.time.LocalDate fromDate,
                                @Param("toDate") java.time.LocalDate toDate,
                                @Param("excludeId") Long excludeId,
                                @Param("inactiveStatuses") java.util.Collection<LeaveStatus> inactiveStatuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE LeaveRequest l SET l.status = :status, l.adminRemarks = :remarks WHERE l.id = :id AND l.status = :pendingStatus")
    int updatePendingStatus(@Param("id") Long id,
                            @Param("status") LeaveStatus status,
                            @Param("remarks") String remarks,
                            @Param("pendingStatus") LeaveStatus pendingStatus);
}
