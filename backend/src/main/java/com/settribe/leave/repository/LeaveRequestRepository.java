package com.settribe.leave.repository;

import com.settribe.leave.entity.LeaveRequest;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    // Ownership check: returns empty if the request belongs to someone else
    Optional<LeaveRequest> findByIdAndUserId(Long id, Long userId);

    // Employee view: own requests, optional filters (null = no filter)
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

    // Admin view: everyone's requests, optional filters; JOIN FETCH avoids lazy-load errors
    @Query("""
            SELECT l FROM LeaveRequest l JOIN FETCH l.user
            WHERE (:status IS NULL OR l.status = :status)
              AND (:type IS NULL OR l.leaveType = :type)
            ORDER BY l.createdAt DESC
            """)
    List<LeaveRequest> findAllFiltered(@Param("status") LeaveStatus status,
                                       @Param("type") LeaveType type);
}
