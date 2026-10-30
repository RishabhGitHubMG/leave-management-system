package com.settribe.leave.repository;

import com.settribe.leave.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Query("""
            SELECT a FROM AuditEvent a
            LEFT JOIN a.actor actor
            WHERE (:actorEmail IS NULL OR LOWER(actor.email) = LOWER(:actorEmail))
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt < :to)
            """)
    Page<AuditEvent> search(@Param("actorEmail") String actorEmail,
                            @Param("action") String action,
                            @Param("entityType") String entityType,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            Pageable pageable);
}
